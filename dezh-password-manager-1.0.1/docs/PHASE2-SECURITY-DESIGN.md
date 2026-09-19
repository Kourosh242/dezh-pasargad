# طراحی معماری امنیتی — فاز ۲ (دژ پاسارگاد)

> محدودهٔ این سند: Security Architecture · Cryptographic Design · Key Management · Master Password · Unlock · Lock Lifecycle
> اصل پایه: fail-closed · least privilege · هیچ secretای در log/exception/navigation/String persistence

---

## ۱) مدل تهدید (Threat Model)

| تهدید | دفاع طراحی‌شده در این فاز |
|---|---|
| خواندن فایل‌های دادهٔ اپ (backup سرد، root بدون قفل صفحه، استخراج data dir) | دادهٔ حساس فقط با AES-256-GCM با کلید تصادفی (DEK) ذخیره می‌شود؛ DEK فقط پس از KDF رمز اصلی قابل بازگشایی است |
| حملهٔ offline dictionary روی فایل keywrap | KDF سخت (PBKDF2-HMAC-SHA256، 600,000 iteration، salt تصادفی ۱۶ بایتی) + تلاش برای دستکاری پارامترها با لایهٔ integrity مبتنی بر AndroidKeyStore خنثی می‌شود |
| دستکاری پارامترهای KDF در فایل (کاهش iterations برای حملهٔ سریع‌تر) | پارامترها در فایل meta رمزنگاری‌شده با کلید AndroidKeyStore نگهداری و cross-check می‌شوند؛ ناهم‌خوانی = فایل خراب |
| brute-force تعاملی از داخل اپ | Exponential backoff روی تلاش‌های ناموفق (۱→۲→۴→۸→۱۵ ثانیه، سقف ۱۵ ثانیه) + شمارندهٔ تلاش با integrity در فایل meta |
| رمز اصلی در حافظه | رمز به‌صورت `char[]` و فقط در طول KDF زنده است؛ بعد از derive، بافر با `PBEKeySpec.clearPassword()` و `fill(0)` پاک می‌شود. DEK فقط در `VaultSession` در حافظهٔ unlock است و هنگام lock با `fill(0)` صفر می‌شود |
| نشت رمز در log/exception | لایه‌های cryptography/security/data-security **هیچ log call ندارند**؛ exceptionها پیام ثابت و بدون داده دارند؛ test خودکار این را چک می‌کند |
| nonce reuse در GCM | nonce ۱۲ بایتی همیشه از `SecureRandom` و per-encryption تولید می‌شود؛ با احتمال غالب هیچ nonceای تکرار نمی‌شود + test یکتایی |
| حمله به فرمت (truncation/version downgrade) | فرمت versioned با magic bytes و طول‌های صریح؛ parse سخت‌گیر → `CorruptedContainer` / `UnsupportedVersion` |
| جعل UI/navigation برای گرفتن secret | قانون معماری: هیچ secretای در navigation arguments؛ صفحات فقط ورودی می‌گیرند و به UseCase می‌دهند |

خارج از محدودهٔ این فاز (در فازهای بعد): tamper detection کامل سیستم، root detection، محافظت در برابر malware باAccessibility، حافظهٔ swap تصویری.

---

## ۲) سلسله‌مراتب کلید (Key Hierarchy)

```
Master Password (کاربر — هرگز ذخیره نمی‌شود، char[], عمر کوتاه)
        │
        │  PBKDF2-HMAC-SHA256 (KDF id=1)
        │  salt تصادفی 16B · 600,000 iteration · خروجی 256 bit
        ▼
Wrapping Key (KEK) — فقط در حافظه، هیچ‌وقت ذخیره نمی‌شود
        │
        │  AES-256-GCM (nonce تصادفی 12B · tag 128 bit)
        ▼
Data Encryption Key (DEK) — تصادفی 256 bit از SecureRandom
   ├── [Wrapped by KEK]  →  فایل keywrap.v1 (روی دیسک، قفل‌شده با رمز اصلی)
   └── [In-memory only]  →  VaultSession (بعد از unlock؛ هنگام lock صفر می‌شود)
                               │
                               │  AES-256-GCM (nonce per-encryption)
                               ▼
                          دادهٔ آیندهٔ Vault (فاز ۳+)
```

**نقش AndroidKeyStore:** کلید AES-256-GCM غیرقابل‌استخراج (`dezh_security_meta_v1`) فقط برای **رمزنگاری + integrity فایل meta** (پارامترهای KDF، شمارندهٔ تلاش ناموفق، زمان آخرین خطا). با این کار پارامترهای KDF قابل دستکاری نیستند و شمارندهٔ brute-force قابل reset با ویرایش فایل نیست. این کلید هیچ دادهٔ vaultای را نمی‌پوشاند و جایگزین رمز اصلی نیست.

**چرا DEK مستقل از رمز؟** تغییر رمز اصلی فقط re-wrap کوتاه را نیاز دارد، رمزنگاری کل داده را از نو نمی‌خواهد؛ و رمز اصلی هرگز به‌عنوان کلید رمزنگاری مستقیم استفاده نمی‌شود (KDF جدا).

**چرا Biometric این فاز کد ندارد؟** Biometric امن به معنای wrap کردن DEK با کلید Keystoreِ `setUserAuthenticationRequired(true)` + `BiometricPrompt.CryptoObject` است؛ در غیر این صورت امنیت را پایین می‌آورد. طبق الزام «اختیاری بودن» و «کامل کار کردن بدون سخت‌افزار»، این مسیر به sub-phase خودش موکول شد (design slot دارد، کد stub ننوشتیم).

---

## ۳) فرمت‌های رمزنگاری (Versioned)

### 3.1. Key Wrap Container — `keywrap.v1` (فایل: `files/dezh/security/keywrap.v1`)

```
offset  size  فیلد
0       4     magic "DPKW"
4       1     formatVersion = 1
5       1     algorithmId = 1 (AES-256-GCM)
6       1     kdfId = 1 (PBKDF2-HMAC-SHA256)
7       4     iterations (uint32, big-endian)
11      1     saltLen (16)
12      16    salt (SecureRandom)
28      1     nonceLen (12)
29      12    nonce (SecureRandom — هر wrap منحصربه‌فرد)
41      4     payloadLen (uint32 BE)
45      n     ciphertext+tag (DEK وrapped با KEK؛ tag 16B داخل payload)
```

### 3.2. Data Encryption Container v1 (برای دادهٔ vault از فاز ۳ به بعد)

```
0       4     magic "DPVG"
4       1     formatVersion = 1
5       1     algorithmId = 1 (AES-256-GCM)
6       1     keyId (رزرو: 0 = session DEK)
7       1     nonceLen (12)
8       12    nonce
20      4     payloadLen
24      n     ciphertext+tag (AES-256-GCM با DEK)
```

### 3.3. Security Meta — رمزنگاری‌شده با AndroidKeyStore (فایل: `meta.v1`)

پیش از رمزنگاری (باینری):
```
u8 kdfId · u32 iterations · u32 failedAttemptCount · s64 lastFailedAtMs
```
پس از رمزنگاری: `iv(12) || ciphertext+tag (AES-256-GCM، کلید AndroidKeyStore)`

### 3.4. قواعد GCM

- nonce ۱۲ بایتی، همیشه random از `SecureRandom`، هرگز شمارشی یا ثابت نیست.
- tag ۱۶ بایتی (128 bit) الزامی؛ بدون tag هیچ payloadای قبول نمی‌شود (تمام رمزها authenticated).
- کلید فقط یک‌بار برای decrypt استفاده می‌شود و بعد از خطای tag، داده دور ریخته می‌شود (fail-closed).
- AAD در فرمت نسخهٔ ۱ استفاده نمی‌شود (رزرو برای binding metadata در نسخه‌های بعد).

---

## ۴) جریان‌های احراز (Authentication Flows)

### 4.1. First Launch Setup
```
Startup → initialize(): فایل keywrap وجود دارد؟
   ├── نه → state=NotSetUp → صفحهٔ Onboarding
   │      رمز + تأییدیه → PasswordStrengthValidator (حداقل: طول ≥10، ≥3 دستهٔ
   │      کاراکتری از {حرف، رقم، نماد، حرف بزرگ}، غیر از لیست رایج)
   │      → generate salt+DEK → KDF → wrap DEK → ذخیره keywrap
   │      → meta رمزنگاری‌شده با Keystore → state=Unlocked
   └── بله → state=Locked → صفحهٔ Unlock
```

### 4.2. Unlock
```
رمز (char[]) → backoff check (meta) → KDF(salt/iterations از فایل) → KEK
  → unwrap DEK با AES-GCM:
      tag معتبر  → attempts=0 ذخیره → VaultSession.unlock(dek) → state=Unlocked
      tag نامعتبر → WrongMasterPasswordException (پیام ثابت، بدون هیچ داده)
                    → attempts++ و lastFailedAt ذخیره → backoff برای تلاش بعدی
```

### 4.3. Lock Lifecycle
| رویداد | رفتار |
|---|---|
| قفل دستی | `VaultSession.lock()` → صفر کردن DEK → state=Locked → UI به Unlock می‌رود |
| خروج به پس‌زمینه | `AutoLockController` تایمر می‌سازد (پیش‌فرض فاز ۲: ۳۰ ثانیه؛ قابل‌تنظیم از فاز Settings) |
| بازگشت قبل از انقضا | تایمر لغو، session حفظ |
| انقضای تایمر | `lock()` خودکار |
| restart اپ / recreate پروسه | DEK فقط در RAM است → پروسهٔ نو = حتماً Locked (fail-closed)؛ session هرگز persist نمی‌شود |
| پاک شدن داده / نصب مجدد | فایل‌ها حذف؛ اگر کلید Keystore هم حذف شود meta decrypt نمی‌شود → treat as NotSetUp (setup مجدد) |

### 4.4. Re-authentication
هر قفل (دستی/خودکار/پروسهٔ جدید) مسیر یکسان Unlock را می‌طلبد؛ هیچ میان‌بری برای «شاید کاربر همان است» وجود ندارد. در فاز biometric، re-auth با `CryptoObject` به همین گره وصل می‌شود.

---

## ۵) معماری لایه‌ها (ادامهٔ فاز ۱)

```
presentation/onboarding · presentation/unlock · presentation/home
        (Compose فقط UI؛ ViewModels با StateFlow)
                    │
domain:  VaultSecurityRepository (interface) · SetupMasterPasswordUseCase ·
         UnlockVaultUseCase · LockVaultUseCase · PasswordStrengthValidator (pure Kotlin)
                    │
data/security: FileVaultSecurityRepository (impl) + VaultSecurityStorage (فایل‌ها)
                    │
cryptography: PasswordKdfEngine(PBKDF2) · AesGcmCipher · KeyWrapper ·
              EncryptionContainer · KeyWrapCodec   (pure Kotlin، بدون android.*)
security: VaultSession (StateFlow lock state) · KeystoreGateway (AndroidKeyStore) ·
          SecurityMetaCodec · LoginAttemptPolicy · AutoLockController
```

DI: ادامهٔ رویکرد دستی (`AppContainer`) — Hilt همچنان ممنوع تا نیاز واقعی احراز شود.
Cryptography/security به‌عنوان pure Kotlin طراحی شده‌اند تا unit test مستقیم روی JVM ممکن باشد؛ `KeystoreGateway` interface است (در تست‌ها Fake، در اپ AndroidKeyStore).

---

## ۶) ملاحظات پیاده‌سازی حساس

- `String` رمز از TextField اجتناب‌ناپذیر است (Compose API)؛ بلافاصله به `char[]` تبدیل، به UseCase داده و ارجاع UI پاک می‌شود. پاک‌سازی کامل String از GC عدم قطعیت دارد — به‌صورت صادقانه در محدودیت‌ها ثبت می‌شود.
- KDF روی `Dispatchers.Default` اجرا می‌شود (هرگز روی Main).
- فایل‌ها در `filesDir` خصوصی اپ (sandbox لینوکسی اپ)؛ هیچ رمزی در SharedPreferences/DB/external storage.
- هیچ usage از `java.util.Random`، ECB، یا password-as-key وجود ندارد (test + review).
- کلیدهای Keystore: `setRandomizedEncryptionRequired(true)` (پیش‌فرض) → IV تولید سمت Keystore و ذخیرهٔ صریح IV در فایل.

## ۷) معیار پذیرش این فاز

build سبز + همهٔ unit testهای فهرست‌شده سبز + lint بدون error + رد شدن چک‌لیست review امنیتی + هیچ قابلیت CRUD/backup/settings اضافه‌ای.
