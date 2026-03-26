# Backend API Endpoints

Bu belge, uygulamanın frontend tarafında `fetch` kullanılarak backend'e atılan tüm isteklerin listesini, HTTP metodunu, gerekli header ve varsa body parametrelerini içermektedir.

Bütün yetkilendirme gerektiren isteklere header olarak yetkilendirme tokenı gönderilmektedir. JSON body giden isteklere ayrıca `Content-Type: application/json` headerı eklenir.

**Genel yetkilendirme Header'ı:**
```json
{
  "Authorization": "Bearer <token>"
}
```

---

## 1. Authentication (Kimlik Doğrulama)

### Login İşlemi
- **URL:** `/api/auth/login`
- **Method:** `POST`
- **Headers:** `{"Content-Type": "application/json"}`
- **Body:**
```json
{
  "email": "user@example.com",
  "password": "user_password"
}
```

---

## 2. Dashboard

### Superadmin Overview
- **URL:** `/api/dashboard/overview`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Admin Overview
- **URL:** `/api/dashboard/admin/overview`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Admin Health
- **URL:** `/api/dashboard/admin/health`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Manager Overview
- **URL:** `/api/dashboard/manager/overview`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Manager Health
- **URL:** `/api/dashboard/manager/health`
- **Method:** `GET`
- **Headers:** Sadece Authorization

---

## 3. Users (Kullanıcılar)

### Kullanıcıları Getirme
- **URL:** `/api/users`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Yeni Kullanıcı Ekleme
- **URL:** `/api/users`
- **Method:** `POST`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "userEmail": "user@example.com",
  "password": "user_password",
  "role": "user",
  "companyId": "company_id" // Opsiyonel olabilir (manager'lar şirket seçmez)
}
```

### Kullanıcı Rolü Güncelleme
- **URL:** `/api/users/${userId}/role`
- **Method:** `PATCH`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "role": "manager"
}
```

### Kullanıcı Silme
- **URL:** `/api/users/${userId}`
- **Method:** `DELETE`
- **Headers:** Sadece Authorization

---

## 4. Companies (Şirketler)

### Şirketleri Getirme
- **URL:** `/api/companies`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Yeni Şirket Ekleme
- **URL:** `/api/companies`
- **Method:** `POST`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "companyName": "Şirket Adı"
}
```

### Şirket Adı Güncelleme
- **URL:** `/api/companies/${companyId}`
- **Method:** `PUT`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "companyName": "Yeni Şirket Adı"
}
```

### Şirket Silme
- **URL:** `/api/companies/${companyId}`
- **Method:** `DELETE`
- **Headers:** Sadece Authorization

---

## 5. Accounts (WhatsApp Hesapları)

### Tüm Hesapları Getirme (Superadmin)
- **URL:** `/api/accounts?scope=all`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Şirket Hesaplarını Getirme (Admin/Manager)
- **URL:** `/api/accounts?scope=company`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Kullanıcıya Atanmış Hesapları Getirme
- **URL:** `/api/user-accounts`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Yeni Hesap Ekleme
- **URL:** `/api/accounts`
- **Method:** `POST`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "name": "WhatsApp Account 12345",
  "accountType": "whatsapp"
}
```

### Hesabın Durumunu (Aktif/Pasif) Güncelleme
- **URL:** `/api/accounts/${accountId}/status`
- **Method:** `PATCH`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "isActive": true
}
```

### Hesap Adını Güncelleme
- **URL:** `/api/accounts/${accountId}`
- **Method:** `PATCH`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "name": "Yeni Hesap Adı"
}
```

### Hesabı Kullanıcıya Atama
- **URL:** `/api/accounts/${accountId}/assign`
- **Method:** `PUT`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "userId": "user_id"
}
```

### Bağlantısı Kesilmiş Hesabı Temizleme
- **URL:** `/api/accounts/${accountId}/cleanup`
- **Method:** `DELETE`
- **Headers:** Sadece Authorization

### Hesap Silme
- **URL:** `/api/accounts/${accountId}`
- **Method:** `DELETE`
- **Headers:** Sadece Authorization

### Hesabın Sıralamasını Değiştirme
- **URL:** `/api/accounts/${accountId}/company-order`
- **Method:** `PUT`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "companyOrder": 1
}
```

---

## 6. Chats & Messaging (Sohbetler ve Mesajlaşma)

### Tüm Sohbetleri Getirme
- **URL:** `/api/chats/all`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Belirli Bir Hesabın Sohbetlerini Getirme
- **URL:** `/api/chats/${accountId}`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Belirli Bir Sohbetin Geçmiş Mesajlarını Getirme
- **URL:** `/api/chats/${accountId}/${chatId}/messages`
- **Method:** `GET`
- **Headers:** Sadece Authorization

### Sohbete Özel İsim (Custom Title) Kaydetme/Güncelleme
- **URL:** `/api/chats/${accountId}/${chatId}/title`
- **Method:** `PUT`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "customTitle": "@YeniBaslik"
}
```

### Sohbete Özel İsmi (Custom Title) Silme
- **URL:** `/api/chats/${accountId}/${chatId}/title`
- **Method:** `DELETE`
- **Headers:** Sadece Authorization

### Mesaja Reaksiyon (Reaction) Gönderme veya Kaldırma
- **URL:** `/api/chats/${accountId}/send-reaction`
- **Method:** `POST`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "remoteJid": "whatsapp_number@s.whatsapp.net",
  "messageId": "whatsapp_message_id",
  "emoji": "👍" // Kaldırmak için boş string "" gönderilir.
}
```

### Medya Mesajı (Resim, Video, Dosya vb.) Gönderme
- **URL:** `/api/chats/${accountId}/send-media`
- **Method:** `POST`
- **Headers:** `{"Content-Type": "application/json", "Authorization": "Bearer <token>"}`
- **Body:**
```json
{
  "remoteJid": "whatsapp_number@s.whatsapp.net",
  "mediaType": "image", // "image", "video", "audio", "document"
  "fileName": "test.png",
  "mimeType": "image/png",
  "caption": "Resim açıklaması", // İsteğe bağlı
  "mediaData": "data:image/png;base64,...", // Base64 kodlanmış veri
  "viewOnce": false // Bir kez görüntüle özelliği (opsiyonel)
}
```

*Not: Normal (text) mesajlar WebSocket (`send_message` event'i ile) üzerinden atılmaktadır, media mesajları Restful API ile gönderiliyor.*
