# ViTranslate 🎤🌐

Ứng dụng Android phiên dịch 2 chiều Việt ↔ Ngoại ngữ + dịch phim không phụ đề.
100% chạy trên máy (offline) sau lần tải mô hình đầu tiên.

## Tính năng

### 1. Hội thoại trực tiếp 2 chiều
- Bấm nút **xanh** → bạn nói tiếng Việt → máy dịch sang ngoại ngữ đã chọn và **đọc to**.
- Bấm nút **đỏ** → người nước ngoài nói → máy dịch sang tiếng Việt và đọc to.
- **Tự phát hiện ngôn ngữ** (ML Kit Language ID): bấm nhầm nút thì app vẫn tự đảo chiều dịch cho đúng.
- Hỗ trợ **tai nghe Bluetooth**: bật công tắc để dùng micro của tai nghe (kênh SCO), âm thanh dịch cũng phát ra tai nghe.
- 9 ngôn ngữ: Anh, Trung, Nhật, Hàn, Pháp, Đức, Tây Ban Nha, Nga, Thái.

### 2. Chế độ dịch phim 🎬
- Dùng **AudioPlaybackCapture** (Android 10+) để nghe âm thanh đang phát trên máy.
- Nhận dạng lời thoại bằng **Vosk** (offline, mô hình ~40–70 MB tải 1 lần).
- Dịch bằng **ML Kit Translate** (offline) và hiện **phụ đề nổi** đè lên mọi app.
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
