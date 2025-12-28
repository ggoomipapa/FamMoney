# Firebase Cloud Functions 배포 가이드

## 개요
그룹 멤버 간 거래 알림을 전송하기 위한 Cloud Functions입니다.

### 구현된 기능
1. **onTransactionCreated**: 새 거래 생성 시 그룹 멤버에게 알림
2. **onMemberJoined**: 새 멤버 참여 시 기존 멤버에게 알림
3. **onSavingsContributionCreated**: 목표 저축 기여 시 알림

---

## 배포 방법

### 1. Node.js 설치 확인
```bash
node --version  # v18 이상 필요
npm --version
```

### 2. Firebase CLI 설치
```bash
npm install -g firebase-tools
```

### 3. Firebase 로그인
```bash
firebase login
```

### 4. 프로젝트 디렉토리로 이동
```bash
cd "C:\Users\one mini\Documents\Project Finished\SelectMoney"
```

### 5. Firebase 프로젝트 연결 확인
```bash
firebase use --add
# 프로젝트 목록에서 선택
```

### 6. Functions 의존성 설치
```bash
cd functions
npm install
cd ..
```

### 7. Functions 배포
```bash
firebase deploy --only functions
```

---

## 배포 후 확인

### Firebase Console에서 확인
1. [Firebase Console](https://console.firebase.google.com) 접속
2. 프로젝트 선택 → Functions 메뉴
3. 배포된 함수 3개 확인:
   - `onTransactionCreated`
   - `onMemberJoined`
   - `onSavingsContributionCreated`

### 로그 확인
```bash
firebase functions:log
```

---

## 테스트 방법

1. **거래 알림 테스트**
   - 두 대의 기기에서 같은 그룹에 참여
   - 한 기기에서 거래 발생 (입금/지출 알림)
   - 다른 기기에서 푸시 알림 수신 확인

2. **설정 확인**
   - 설정 → "내 거래 그룹에 알림" 활성화
   - 설정 → "그룹 알림 수신" 활성화

---

## 문제 해결

### 알림이 오지 않는 경우

1. **FCM 토큰 확인**
   - 앱을 재설치하거나 재시작하여 토큰 갱신

2. **알림 권한 확인**
   - 기기 설정 → 앱 → SelectMoney → 알림 허용

3. **Cloud Functions 로그 확인**
   ```bash
   firebase functions:log --only onTransactionCreated
   ```

4. **Firestore 규칙 확인**
   - users 컬렉션에서 fcmToken 필드 읽기 권한 필요

### 배포 실패 시

1. **Blaze 요금제 필요**
   - Cloud Functions는 Blaze (종량제) 요금제 필요
   - Firebase Console → 업그레이드 클릭

2. **Node.js 버전 확인**
   - `functions/package.json`의 `engines.node`와 로컬 버전 일치 확인

---

## 비용 안내

- Cloud Functions: 월 200만 호출 무료
- FCM: 무료 (무제한)
- 일반적인 사용에서는 무료 티어 내에서 충분함
