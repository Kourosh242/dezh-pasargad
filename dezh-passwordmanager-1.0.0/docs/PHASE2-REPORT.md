# گزارش فاز ۲ — دژ پاسارگاد: Security Architecture & Cryptographic Design

> تاریخ: 2026-09-16 · وضعیت گیت فاز ۲: **سبز — build + 64/64 tests + detekt + lint + security review**
> محدودهٔ انجام‌شده: Security Architecture · Cryptographic Design · Key Management · Master Password · Unlock · Lock Lifecycle
> خارج از محدوده (طبق فرمان): CRUD کامل Vault، Backup/Restore، Settings کامل — هیچ‌کدام پیاده نشد.

---

## ۱) Security Architecture (طراحی → پیاده‌سازی)

سند طراحی **قبل از پیاده‌سازی** نوشته و commit شد: `docs/PHASE2-SECURITY-DESIGN.md` (شامل مدل تهدید، سلسله‌مراتب کلید، فرمت‌ها، جریان‌ها).

لایه‌بندی اجراشده (ادامهٔ معماری فاز ۱):

```
presentation/setup · presentation/unlock · presentation/home   (Compose، بدون منطق کسب‌وکار)
        │ ViewModels با StateFlow (SetupViewModel · UnlockViewModel · VaultHomeViewModel)
domain/    : VaultSecurityRepository (interface) · UseCaseهای Setup/Unlock/Lock ·
             PasswordStrengthValidator (pure Kotlin) · VaultLockState
cryptography/: PasswordKdfEngine(PBKDF2) · AesGcmCipher · KeyWrapper ·
               KeyWrapContainer · EncryptionContainer  (pure Kotlin، بدون android.*)
security/  : VaultSession (تنها دارندهٔ DEK در RAM) · AndroidKeystoreGateway ·
             SecurityMetaCodec · VaultSecurityStorage · LoginAttemptPolicy · AutoLockController
data/security: FileVaultSecurityRepository (پیاده‌سازی domain interface)
di/        : AppContainer (DI دستی — Hilt همچنان توجیه نشده)
```

Navigation از قفل پیروی می‌کند: ریشهٔ backstack از `StateFlow<VaultLockState>` بازسازی می‌شود؛ کاربر Locked هرگز صفحهٔ Unlocked را نمی‌بیند و برعکس. مسیرهای ناوبری همه `data object` بدون فیلد هستند → **هیچ secretای در navigation arguments وجود ندارد** (اثبات: grep + ساختار `DezhDestination`).

## ۲) Key Lifecycle

```
Master Password (char[] — فقط در طول KDF زنده است، بعدش صفر می‌شود)
   │ PBKDF2-HMAC-SHA256 · salt تصادفی 16B · 600,000 iteration · خروجی 256-bit
   ▼
KEK (wrapping key — هرگز ذخیره نمی‌شود، در finally صفر می‌شود)
   │ AES-256-GCM · nonce تصادفی 12B · tag 128-bit
   ▼
DEK (تصادفی 256-bit از SecureRandom — هرگز از رمز مشتق نمی‌شود)
   ├── روی دیسک: فقط به‌صورت wrapped داخل keywrap.v1
   └── در حافظه: فقط داخل VaultSession؛ در lock/restart صفر و حذف می‌شود
```

نقش‌ها:
- **AndroidKeyStore**: کلید غیرقابل‌استخراج AES-256-GCM (`dezh_security_meta_v1`) فقط برای رمزنگاری + integrity فایل meta (پارامترهای KDF + شمارندهٔ تلاش ناموفق). دستکاری پارامترهای KDF (مثلاً کاهش iterations) با cross-check بین wrap و meta کشف می‌شود (test دارد).
- **Session**: `VaultSession` — DEK هرگز از این کلاس خارج نمی‌شود؛ `lock()` بایت‌ها را صفر می‌کند (test دارد)؛ encrypt/decrypt فقط از طریق session انجام می‌شود.
- **Auto-lock**: `AutoLockController` + ProcessLifecycleOwner — پیش‌فرض ۳۰ ثانیه در پس‌زمینه (فاز Settings قابل‌تنظیم می‌کند). restart/recreate پروسه → همیشه Locked (fail-closed، هیچ state unlocked ای persist نمی‌شود).
- **Re-authentication**: هر قفل (دستی/خودکار/restart) فقط با مسیر Unlock باز می‌شود.

## ۳) Encryption Format (versioned)

- **keywrap.v1** (`DPKW`): magic(4) + version(1) + algorithmId(1=AES-256-GCM) + kdfId(1=PBKDF2-SHA256) + iterations(u32) + saltLen(1)+salt(16) + nonceLen(1)+nonce(12) + payloadLen(u4) + ciphertext‖tag — metadata کامل برای decrypt را دارد.
- **Data container v1** (`DPVG`) برای دادهٔ vault از فاز ۳: magic + version + algorithmId + keyId + nonce + payload‖tag (آماده و تست‌شده).
- **meta.v1**: `kdfId(u8) | iterations(u32) | failedAttemptCount(u32) | lastFailedAtMs(s64)` رمزنگاری‌شده با کلید Keystore (`iv(12)‖ct‖tag`).
- قواعد GCM: nonce ۱۲ بایتی همیشه random و per-encryption (test یکتایی ۵۰۰ نمونه)، tag الزامی ۱۲۸-bit، هیچ حالت unauthenticated/ECB در کد وجود ندارد (grep review + کد).

## ۴) Authentication Flow

- **First launch**: `initialize()` → فایل keywrap وجود ندارد → `NotSetUp` → صفحهٔ Onboarding.
- **Setup**: وارد کردن رمز + تأییدیه + سنجش زندهٔ قدرت (حداقل ۱۰ نویسه، ≥۳ دستهٔ کاراکتری Unicode-aware شامل حروف فارسی، لیست رمزهای رایج، تکرار نویسه) → تولید salt/DEK → KDF → wrap → ذخیره (نوشتن اتمیک؛ خطا → پاک‌سازی کامل) → `Unlocked`.
- **Unlock**: backoff check → KDF → unwrap با GCM (auth tag = verifier؛ رمز ذخیره/مقایسه نمی‌شود):
  - رمز غلط → `WrongMasterPassword` + backoff نمایی (۱/۲/۴/۸/۱۵ ثانیه، سقف ۱۵) با شمارندهٔ persist شده و countdown در UI؛ پیام‌ها generic و بدون جزئیات.
  - tag نامعتبر = رمز غلط یا دستکاری → همان رفتار (تفکیک‌ناپذیری عمدی).
- **فایل خراب/دستکاری‌شده** → `VaultDataCorrupted` → پیام generic، fail-closed.
- **Biometric**: طبق الزام «اختیاری» و «بدون تضعیف امنیت رمز اصلی»، کد stub ننوشتیم؛ design slot در سند طراحی موجود است (نیازمند کلید auth-bound + BiometricPrompt.CryptoObject — sub-phase مستقل). اپ بدون biometric کاملاً کار می‌کند.

## ۵) Test Results (64/64 سبز)

| Suite | تعداد | پوشش |
|---|---|---|
| AesGcmCipherTest | 9 | roundtrip، wrong key، tamper tag/ct، truncation، یکتایی ۵۰۰ nonce، تفاوت ciphertextها، AAD mismatch، اندازهٔ کلید نامعتبر |
| KeyWrapperTest | 7 | wrap/unwrap، رمز غلط، salt/nonce یکتا per-wrap، tamper، ساختار خراب، قطعیت KDF و تفاوت salt، metadata فرمت |
| EncryptionContainerTest | 6 | roundtrip، magic، version downgrade، algorithm، truncation، payload< tag |
| PasswordStrengthValidatorTest | 9 | کوتاه، رایج، دستهٔ ناکافی، Fair/Strong، **رمز فارسی**، حروف فارسی تنها، تکرار، بیش‌ازحد بلند |
| LoginAttemptPolicyTest | 4 | رشد نمایی و سقف، کاهش با زمان، بدون خطا=بدون تاخیر، clamp |
| SecurityMetaCodecTest | 3 | roundtrip، truncation، اندازهٔ ثابت |
| VaultSessionTest | 7 | stateها، چسبندگی NotSetUp، کپی کلید، صفر شدن در lock، fail-closed، roundtrip، encrypt بعد از lock |
| AutoLockControllerTest | 4 | قفل با timeout مجازی، لغو با foreground، بی‌اثر روی locked، ریست تایمر |
| FileVaultSecurityRepositoryTest | 11 | setup/unlock/lock، رمز غلط+backoff رشدی، keywrap خراب، tamperهای سه‌گانه (payload، meta، iterations)، **non-persistence رمز و DEK**، initialize دو-مرحله‌ای با Keystore مشترک، setup مجدد ممنوع، payload بدون DEK |
| NoSensitiveDataTest | 3 | exceptionها بدون secret، خطاهای repository بدون secret، **اسکن استاتیک: صفر log call در پکیج‌های امنیتی** |

نکته: KDF در تست‌ها با iterations کم (۲۰۰۰) اجرا می‌شود تا سریع باشد؛ مقدار production (۶۰۰k) فقط از مسیر AppContainer می‌رود.

## ۶) Build / Quality Pipeline Result

به ترتیب الزام‌شده (همه در یک invocation نهایی، `EXIT=0`):

| مرحله | نتیجه |
|---|---|
| BUILD (`:app:assembleDebug`) | ✅ `dezh-pasargad-debug.apk` |
| UNIT TESTS (`:app:testDebugUnitTest`) | ✅ 64/64 |
| LINT (`:app:lintDebug`) | ✅ 0 error · 3 warning اطلاع‌رسانی: `AndroidGradlePluginVersion` + `GradleDependency` (پیشنهاد نسخهٔ جدیدتر — به‌عمد رد شد؛ baseline policy) و `DataExtractionRules` (به فاز backup/امنیت موکول شده) |
| STATIC ANALYSIS (`:app:detekt` 1.23.8) | ✅ 0 یافته (config: `config/detekt/detekt.yml` — MagicNumber/ThrowsCount فقط در کد فرمت باینری با Suppress موضعی مستند شده) |
| SECURITY REVIEW | ✅ چک‌لیست شواهد (بخش ۷) |
| RELEASE (`:app:assembleRelease`) | ✅ `dezh-pasargad-release-unsigned.apk` |

خطاهای میانی که root cause رفع شدند (بدون تغییر نسخه‌ها):
1. OOM daemon هنگام configuration (dmesg: kill در RSS 1.5GB) → توازن JVM: `-Xmx640m -XX:MaxMetaspaceSize=400m -XX:ReservedCodeCacheSize=48m` + `workers.max=1`.
2. تست `should be void` → assertThrows در انتهای expression body مقدار برمی‌گرداند → `(): Unit`.
3. کلاسیفیکیشن یونیکد قدرت رمز → حروف caseless فارسی «نماد» شمرده می‌شد → ترتیب isDigit→isLetter→symbol.
4. تست initialize با FakeKeystoreGateway متفاوت بین دو repository → کلید Keystore باید بین restartها مشترک باشد (مطابق دنیای واقعی).
5. ویرایش plurals در UnlockScreen روی دیسک اعمال نشده بود (ناسازگاری ابزار) → با assert بازنویسی و verify شد.

## ۷) Security Review Checklist (شواهد اجرا شده)

| الزام | نتیجهٔ بررسی |
|---|---|
| master password ذخیره نشود | ✅ تست non-persistence: رمز در هیچ فایلی نیست؛ فقط verifier غیرمستقیم (wrapped DEK) |
| master password log نشود | ✅ صفر log call در پکیج‌های امنیتی (test + grep) |
| رمز در exception/log نباشد | ✅ exceptionها static message (test) |
| کلید hardcode نشود | ✅ grep: هیچ literal هگز/base64 کلیدمانند |
| AES بدون authentication | ✅ فقط `AES/GCM/NoPadding` با tag 128 (grep + کد) |
| ECB | ✅ غایب |
| Random معمولی برای secret | ✅ فقط `java.security.SecureRandom` (grep) |
| تبدیل مستقیم رمز به AES key | ✅ همیشه از مسیر KDF + salt تصادفی |
| fake encryption | ✅ JCA استاندارد (`javax.crypto`)، Keystore واقعی Android، جایگزین‌سازی مسکین نیست |
| GCM nonce reuse | ✅ random per-encryption + test یکتایی |
| secret در navigation | ✅ مسیرها parameterless |

## ۸) Security Limitations (صادقانه)

1. **«۱۰۰٪ امن» یا «شکست‌ناپذیر» ادعا نمی‌شود.** این پیاده‌سازی تلاش مهندسی‌شده برای دفاع در برابر تهدیدهای فهرست مدل تهدید است.
2. **String رمز در UI**: Compose TextField خروجی String دارد؛ بلافاصله به char[] تبدیل و ارجاع‌ها صفر می‌شوند، اما کپی‌های String در heap تا GC باقی می‌مانند (محدودیت پلتفرم؛ در مستندات APIهای کلید فارسی هم ثبت شد).
3. **PBKDF2** استاندارد و قابل‌حمل است ولی در برابر GPU/ASIC از Argon2id ضعیف‌تر است؛ ارتقا به KDF حافظه‌سخت (با وابستگی معتبر) به‌عنوان بهبود آینده ثبت شد.
4. **AndroidKeystoreGateway در دستگاه واقعی تست نشده** (امولاتور/دستگاه در دسترس نیست)؛ unit testها با double JVM تست شده‌اند — بررسی روی دستگاه واقعی در فاز تحویل لازم است.
5. **Biometric پیاده نشده** (اختیاری/موکول) — slot معماری آماده است.
6. **Auto-lock ثابت ۳۰ ثانیه** است تا فاز Settings؛ سیاست ضد screenshot/blurring و `FLAG_SECURE` در فاز امنیت پیشرفته.
7. **Attack surface فیزیکی** (root، screen capture، سوءاستفاده از Accessibility توسط بدافزار) خارج از محدودهٔ این فاز است.
8. شمارندهٔ تلاش‌ها جلوی brute-force تعاملی را می‌گیرد؛ **حملهٔ offline روی فایل keywrap** فقط به هزینهٔ ۶۰۰k iteration PBKDF2 گران شده (تغییر پارامترها هم integrity-protected است).

## ۹) فایل‌های کلیدی

- طراحی: `docs/PHASE2-SECURITY-DESIGN.md`
- crypto: `cryptography/` (7 فایل) · security: `security/` (7 فایل) · domain: 4 فایل · data: `data/security/FileVaultSecurityRepository.kt`
- UI: `presentation/setup|unlock|home` + `navigation/DezhApp.kt` · DI: `di/AppContainer.kt` · `DezhApplication.kt`
- تست: 10 کلاس در `app/src/test/` · تحلیل استاتیک: `config/detekt/detekt.yml`
