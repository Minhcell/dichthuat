# ViTranslate 🎤🌐

Ứng dụng Android phiên dịch 2 chiều Việt ↔ Ngoại ngữ + dịch phim không phụ đề.
100% chạy trên máy (offline) sau lần tải mô hình đầu tiên.

## Tính năng

### 1. Hội thoại trực tiếp 2 chiều
- Bấm nút **xanh** → bạn nói tiếng Việt → máy dịch sang ngoại ngữ đã chọn và **đọc to**.
- Bấm nút **đỏ** → người nước ngoài nói → máy dịch sang tiếng Việt và đọc to.
- **Tự phát hiện ngôn ngữ** (ML Kit Language ID): bấm nhầm nút thì app vẫn tự đảo chiều dịch cho đúng.
- Hỗ trợ **tai nghe Bluetooth**: bật công tắc để dùng micro của tai nghe (kênh SCO), âm thanh dịch cũng phát ra tai nghe.
- 36 ngôn ngữ thế giới: Anh, Trung, Nhật, Hàn, Pháp, Đức, Tây Ban Nha, Nga, Thái, Ý, Bồ Đào Nha, Hà Lan, Thổ Nhĩ Kỳ, Hindi, Indonesia, Mã Lai, Tagalog, Ả Rập, Ba Tư, Hebrew, Ba Lan, Ukraina, Séc, Thụy Điển, Na Uy, Đan Mạch, Phần Lan, Hy Lạp, Hungary, Romania, Bulgaria, Bengal, Tamil, Urdu, Swahili…

### 2. Chế độ dịch phim 🎬 (2 kiểu)
- Dùng **AudioPlaybackCapture** (Android 10+) để nghe âm thanh đang phát trên máy.
- Nhận dạng lời thoại bằng **Vosk** (offline, 17 ngôn ngữ, mô hình ~40–70 MB tải 1 lần).
- **📖 Dịch phụ đề**: hiện phụ đề tiếng Việt nổi; mỗi câu giữ trên màn hình 2,2s + 75ms/ký tự (tối đa 9s) để đọc kịp, câu mới xếp hàng đợi.
- **🔊 Dịch bằng tiếng**: đọc to bản dịch bằng giọng tiếng Việt qua loa hoặc tai nghe Bluetooth — không cần nhìn chữ. Giọng đọc phát qua kênh ASSISTANCE nên không bị máy ghi nhầm lại (không vòng lặp).
- ⚠️ Netflix, Disney+... chặn ghi âm thanh (DRM) nên không dịch được. YouTube, trình phát video, trình duyệt hoạt động bình thường.

## Công nghệ
| Thành phần | Thư viện |
|---|---|
| Nghe giọng nói (hội thoại) | Android SpeechRecognizer (Google) |
| Nghe âm thanh phim | AudioPlaybackCapture + Vosk offline |
| Dịch | ML Kit Translate (offline) |
| Phát hiện ngôn ngữ | ML Kit Language ID |
| Đọc to | Android TextToSpeech |
| Tai nghe Bluetooth | AudioManager SCO / setCommunicationDevice |

## Build (GitHub Actions)
1. Tạo repo mới trên GitHub, upload toàn bộ thư mục này.
2. Vào tab **Actions** → workflow **Build APK** chạy tự động.
3. Tải file `ViTranslate-debug-apk` trong mục Artifacts về và cài đặt.

Yêu cầu: Android 10 trở lên. AGP 8.5.2 / Gradle 8.7 / JDK 17.
