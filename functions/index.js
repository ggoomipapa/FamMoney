/**
 * SelectMoney Cloud Functions
 * 그룹 멤버 간 거래 알림 전송
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

/**
 * 새 거래가 생성되면 그룹 멤버에게 FCM 알림 전송
 */
exports.onTransactionCreated = functions
  .region("asia-northeast3") // 서울 리전
  .firestore.document("transactions/{transactionId}")
  .onCreate(async (snapshot, context) => {
    const transaction = snapshot.data();
    const transactionId = context.params.transactionId;

    try {
      // 거래 정보 추출
      const {
        groupId,
        userId,
        userName,
        type,
        amount,
        description,
        merchantName,
      } = transaction;

      if (!groupId || !userId) {
        console.log("Missing groupId or userId");
        return null;
      }

      // 거래한 사용자 조회 (알림 전송 설정 확인)
      const userDoc = await db.collection("users").doc(userId).get();
      if (!userDoc.exists) {
        console.log("User not found:", userId);
        return null;
      }

      const user = userDoc.data();

      // 사용자가 그룹에 알림 보내기를 끈 경우
      if (user.notifyGroupOnTransaction === false) {
        console.log("User disabled group notifications");
        return null;
      }

      // 같은 그룹의 다른 멤버들 조회 (알림 수신 설정된 멤버만)
      const membersSnapshot = await db
        .collection("users")
        .where("groupId", "==", groupId)
        .where("receiveGroupNotifications", "==", true)
        .get();

      if (membersSnapshot.empty) {
        console.log("No members to notify");
        return null;
      }

      // FCM 토큰 수집 (본인 제외)
      const tokens = [];
      membersSnapshot.docs.forEach((doc) => {
        const member = doc.data();
        if (doc.id !== userId && member.fcmToken) {
          tokens.push(member.fcmToken);
        }
      });

      if (tokens.length === 0) {
        console.log("No valid FCM tokens");
        return null;
      }

      // 알림 메시지 구성
      const typeText = type === "INCOME" ? "수입" : "지출";
      const formattedAmount = new Intl.NumberFormat("ko-KR").format(amount);
      const descText = merchantName || description || "";

      const message = {
        tokens: tokens,
        data: {
          type: "transaction",
          transactionId: transactionId,
          userName: userName || "멤버",
          transactionType: type,
          amount: String(amount),
          description: descText,
        },
        notification: {
          title: `${userName || "멤버"} 님의 ${typeText}`,
          body: `${formattedAmount}원${descText ? " - " + descText : ""}`,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "group_transaction_channel",
            sound: "default",
          },
        },
      };

      // FCM 멀티캐스트 전송
      const response = await messaging.sendEachForMulticast(message);
      console.log(
        `Sent ${response.successCount} notifications, ` +
          `${response.failureCount} failed`
      );

      // 실패한 토큰 정리 (무효한 토큰 삭제)
      if (response.failureCount > 0) {
        const failedTokens = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            const errorCode = resp.error?.code;
            if (
              errorCode === "messaging/invalid-registration-token" ||
              errorCode === "messaging/registration-token-not-registered"
            ) {
              failedTokens.push(tokens[idx]);
            }
          }
        });

        // 무효한 토큰을 가진 사용자의 fcmToken 필드 삭제
        if (failedTokens.length > 0) {
          const batch = db.batch();
          const usersToUpdate = await db
            .collection("users")
            .where("fcmToken", "in", failedTokens)
            .get();

          usersToUpdate.docs.forEach((doc) => {
            batch.update(doc.ref, { fcmToken: null });
          });

          await batch.commit();
          console.log(`Cleaned up ${failedTokens.length} invalid tokens`);
        }
      }

      return null;
    } catch (error) {
      console.error("Error sending notifications:", error);
      return null;
    }
  });

/**
 * 새 멤버가 그룹에 참여하면 알림 전송
 */
exports.onMemberJoined = functions
  .region("asia-northeast3")
  .firestore.document("users/{userId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();
    const userId = context.params.userId;

    // groupId가 변경된 경우 (새 그룹 참여)
    if (before.groupId !== after.groupId && after.groupId) {
      try {
        const groupId = after.groupId;
        const userName = after.name || "새 멤버";

        // 그룹 정보 조회
        const groupDoc = await db.collection("groups").doc(groupId).get();
        const groupName = groupDoc.exists
          ? groupDoc.data().name || "가계부"
          : "가계부";

        // 같은 그룹의 다른 멤버들 조회
        const membersSnapshot = await db
          .collection("users")
          .where("groupId", "==", groupId)
          .where("receiveGroupNotifications", "==", true)
          .get();

        const tokens = [];
        membersSnapshot.docs.forEach((doc) => {
          const member = doc.data();
          if (doc.id !== userId && member.fcmToken) {
            tokens.push(member.fcmToken);
          }
        });

        if (tokens.length === 0) {
          return null;
        }

        const message = {
          tokens: tokens,
          data: {
            type: "group_join",
            userName: userName,
            groupName: groupName,
          },
          notification: {
            title: "새 멤버 참여",
            body: `${userName} 님이 '${groupName}' 가계부에 참여했습니다.`,
          },
          android: {
            priority: "high",
            notification: {
              channelId: "group_update_channel",
            },
          },
        };

        await messaging.sendEachForMulticast(message);
        console.log(`Sent join notification for ${userName}`);
      } catch (error) {
        console.error("Error sending join notification:", error);
      }
    }

    return null;
  });

/**
 * 목표 저축에 기여가 추가되면 알림 전송
 */
exports.onSavingsContributionCreated = functions
  .region("asia-northeast3")
  .firestore.document("savings_contributions/{contributionId}")
  .onCreate(async (snapshot, context) => {
    const contribution = snapshot.data();

    try {
      const { goalId, userId, userName, amount, isAutoDetected } = contribution;

      // 목표 정보 조회
      const goalDoc = await db.collection("savings_goals").doc(goalId).get();
      if (!goalDoc.exists) {
        return null;
      }

      const goal = goalDoc.data();
      const groupId = goal.groupId;

      // 같은 그룹의 다른 멤버들 조회
      const membersSnapshot = await db
        .collection("users")
        .where("groupId", "==", groupId)
        .where("receiveGroupNotifications", "==", true)
        .get();

      const tokens = [];
      membersSnapshot.docs.forEach((doc) => {
        const member = doc.data();
        if (doc.id !== userId && member.fcmToken) {
          tokens.push(member.fcmToken);
        }
      });

      if (tokens.length === 0) {
        return null;
      }

      const formattedAmount = new Intl.NumberFormat("ko-KR").format(amount);
      const autoText = isAutoDetected ? " (자동 감지)" : "";

      const message = {
        tokens: tokens,
        data: {
          type: "savings_contribution",
          goalId: goalId,
          goalName: goal.name,
          userName: userName,
          amount: String(amount),
        },
        notification: {
          title: `${goal.iconEmoji} ${goal.name} 저축`,
          body: `${userName} 님이 ${formattedAmount}원 저축${autoText}`,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "savings_channel",
          },
        },
      };

      await messaging.sendEachForMulticast(message);
      console.log(`Sent savings contribution notification`);
    } catch (error) {
      console.error("Error sending savings notification:", error);
    }

    return null;
  });
