# Review thay đổi Profile và bảo mật tài khoản
Ngày cập nhật: 10/09/2026.

## 1. Thay đổi người dùng nhìn thấy

Profile được chia thành hai phần độc lập:

1. **Thông tin hiển thị:** đổi tên hiển thị; đổi/xóa URL avatar HTTPS; xem trước ảnh và báo lỗi tải; bỏ thay đổi chưa lưu.
2. **Bảo vệ tài khoản:** đổi username, đặt/đổi mật khẩu app, xác minh lại bằng mật khẩu hiện tại hoặc Google đã liên kết. Lưu thành công yêu cầu đăng nhập lại trên mọi thiết bị.

Ví dụ: tài khoản tạo bằng Google không biết mật khẩu ngẫu nhiên nội bộ. Người dùng chọn xác minh qua Google, chọn đúng tài khoản đã liên kết, nhập mật khẩu app mới hai lần và lưu. Sau đó họ có thể đăng nhập bằng username/mật khẩu app hoặc Google. Thao tác này không đổi mật khẩu Google.

Email vẫn chỉ đọc vì chưa có dịch vụ xác minh địa chỉ mới. Avatar dùng URL, chưa bổ sung upload ảnh. Profile vẫn riêng với Dashboard, không hiển thị ID nội bộ.

## 2. Vấn đề đã xử lý

| Trước | Sau |
|---|---|
| PATCH Profile cho đổi password mà không xác minh lại | Từ chối password; chỉ endpoint bảo mật được đổi |
| Thông tin và password chung form | Tách hai form/nút lưu |
| Username chỉ đọc | Cho đổi sau xác minh, kiểm tra trùng |
| Đổi password không vô hiệu mọi token cũ | Đổi security stamp cùng transaction |
| Người dùng Google không biết password app | Cho xác minh bằng đúng Google identity đã liên kết |

Luồng: phiên hợp lệ → khóa hàng user → giới hạn lượt thử → xác minh danh tính → kiểm tra thay đổi → lưu cùng security stamp mới → xóa refresh cookie → đăng nhập lại.

Mật khẩu mới: ít nhất 15 ký tự, tối đa 72 byte UTF-8 vì encoder hiện dùng BCrypt. Không trim/cắt mật khẩu. Từ chối toàn khoảng trắng, mật khẩu cũ và xác nhận không khớp. Chính sách đăng ký hiện có chưa đổi; chính sách mạnh hơn áp dụng ở endpoint bảo mật mới.

Google token phải được cấp trong 5 phút, qua kiểm tra chữ ký/audience/issuer/expiry/email_verified, và có sub trùng identity đã liên kết. Trùng email là chưa đủ. Đây là token mới; app chưa bắt buộc người dùng nhập lại mật khẩu Google hay thực hiện MFA.

Redis giới hạn 5 lần gọi thay đổi bảo mật/tài khoản/15 phút, gồm cả lần thành công. Lần thứ 6 trả 429. Redis lỗi trả 503, không lưu thông tin. Lua đặt bộ đếm và TTL nguyên tử.

## 3. API cần review

| API | Hợp đồng |
|---|---|
| GET /api/users/me | Hồ sơ hiện tại; không trả password hash/security stamp |
| PATCH /api/users/me | displayName, profilePictureUrl; từ chối password, username, email |
| GET /api/users/me/security | googleLinked: true/false |
| POST /api/users/me/security | Xác minh rồi đổi username và/hoặc mật khẩu |

Body endpoint bảo mật gồm currentPassword HOẶC googleCredential; username tùy chọn; newPassword kèm confirmPassword nếu đổi mật khẩu. Chỉ đổi username thì để hai trường password mới là null.

Username: 3–50 chữ ASCII/số/gạch dưới, kiểm tra phân biệt hoa thường theo hệ thống hiện tại. Đổi username giữ nguyên ID user nên liên kết project/task/expense vẫn trỏ về cùng tài khoản.

## 4. Migration và phiên đăng nhập

Migration mới: backend/src/main/resources/db/migration/V12__add_user_security_stamp.sql.

- Thêm users.security_stamp VARCHAR(36), nullable cho tài khoản cũ.
- Tài khoản cũ giữ phiên cho đến lần đổi bảo mật đầu tiên.
- Tài khoản mới sinh stamp ngẫu nhiên; tái sử dụng username không làm token của chủ username trước đó hợp lệ cho tài khoản mới.
- Session ID có tiền tố stamp; JwtFilter và refresh đối chiếu với database.
- Key phiên cũ trên Redis vẫn hết hạn theo TTL nhưng token đã bị từ chối sau khi transaction commit.
- Request đã xác thực trước commit có thể hoàn tất; không hủy request đang chạy.

V12 nằm sau V11 của phần review submission có sẵn trong working tree. Kiểm thử Profile không chứng nhận V11/workflow review đã hoàn chỉnh. Chưa chạy migration/khóa hàng trên PostgreSQL thật.

**Rollback:** không đơn thuần quay về code bỏ kiểm tra stamp sau khi đã đổi password. Code cũ có thể nhận lại phiên chưa hết TTL. Trước rollback cần thu hồi phiên hoặc xoay khóa ký theo kế hoạch vận hành.

## 5. File chính

| Nhóm | File |
|---|---|
| UI | frontend/src/pages/ProfilePage.jsx; frontend/src/components/auth/AccountSecurityPanel.jsx; frontend/src/pages/LoginPage.jsx; frontend/src/styles.css |
| API/DTO | UserController.java; AccountSecurityRequest.java; UpdateUserRequest.java |
| Xác minh/lưu | AccountSecurityService.java; SecurityChangeLimiter.java; GoogleIdTokenService.java |
| Thu hồi token | User.java; UserRepository.java; AuthService.java; JwtFilter.java |
| Chặn bypass/mapping | UserService.java; UserMapper.java; GlobalExceptionHandler.java |

Java nằm dưới backend/src/main/java/com/taskmanagement theo package tương ứng. GlobalExceptionHandler bổ sung ResponseStatusException để trả đúng HTTP 429.

## 6. Bằng chứng kiểm thử

Bộ test tập trung đã chạy thành công với Maven cache ngoài sandbox:

```powershell
# Chạy tại backend, dùng Maven có sẵn trên PATH hoặc đường dẫn mvn.cmd của máy.
mvn '-Dtest=AccountSecurityServiceTest,AuthServiceTest,JwtServiceTest,GoogleIdTokenServiceTest,JwtFilterSecurityTest,ProfileContractTest' test
```

Test bao phủ: mật khẩu hiện tại sai; xác nhận sai; byte Unicode quá dài; đổi password/stamp; Google identity sai; Google liên kết đặt password app; đổi username giữ ID; chặn access/refresh cũ; chặn password qua PATCH Profile. Google verifier được mock, không phải Google login thật.

Frontend npm.cmd run lint và npm.cmd run build đã qua. git diff --check đã qua, có cảnh báo LF/CRLF trên Windows. Sandbox từng chặn JAR Maven; chạy ngoài sandbox đã vượt lỗi môi trường.

Chưa kiểm chứng: browser thực tế, Google thật, cookie hai thiết bị, Flyway/khóa PostgreSQL, limiter Redis thật. Chưa chạy toàn bộ regression của review submission đang có thay đổi.

## 7. Checklist nghiệm thu

- [ ] Đổi tên/ảnh, xóa ảnh, bỏ thay đổi rồi tải lại Profile.
- [ ] PATCH password bị từ chối; password sai/cũ, xác nhận sai và Unicode quá dài không thay đổi tài khoản.
- [ ] Đăng nhập hai thiết bị; đổi username/password ở một máy; token access/refresh cũ ở máy kia bị từ chối; đăng nhập lại bằng thông tin mới.
- [ ] Đặt password app qua Google đã liên kết; thử Google khác và token quá 5 phút.
- [ ] Chạy Flyway trên PostgreSQL thử nghiệm; kiểm tra lần xác minh thứ 6, Redis lỗi và hai yêu cầu đồng thời.

## 8. Bảo mật nên bổ sung tiếp

Xác minh email mới; quên mật khẩu qua token một lần có hạn; email thông báo thay đổi bảo mật; kiểm tra mật khẩu rò rỉ; MFA/passkey. Các mục này cần luồng/dịch vụ riêng và chưa được triển khai.

Tham khảo: [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html), [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
