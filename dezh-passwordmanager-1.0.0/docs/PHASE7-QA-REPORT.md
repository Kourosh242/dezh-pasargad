# گزارش QA و Audit فاز ۷ — دژ پاسارگاد

**تاریخ:** ۱۶ سپتامبر ۲۰۲۶
**پایه:** commit `0cbaa23` (پایان فاز ۶)
**چرخهٔ اجراشده:** BUILD → UNIT TESTS → UI TESTS → ERROR INSPECT → RCA → FIX → LINT → STATIC ANALYSIS → SECURITY REVIEW → **CLEAN BUILD** → REBUILD → **RETEST** → UI/UX REVIEW → PERFORMANCE REVIEW

**خلاصهٔ اعداد:** ۱۶۴/۱۶۴ تست سبز (۸ تست جدید این فاز) · lint ۰ خطا · detekt ۰ یافته · R8 release build موفق (APK ۳.۱۰MB) · clean build از صفر موفق.

---

## ۱. Authentication — **PASS**

| سناریو | نتیجه | پوشش |
|---------|--------|-------|
| first setup | PASS | `AuthenticationLifecycleTest` + تست‌های FileVaultSecurityRepository |
| correct password | PASS | همان + UnlockViewModel تست‌های فاز ۲ |
| incorrect password | PASS | رد شدن + backoff نمایی + شمارندهٔ ماندگار |
| relock | PASS | session صفر و UI به Unlock |
| restart | **PASS (FIXED)** | تست جدید: بازسازی کامل کامپوننت‌ها روی همان فایل‌ها؛ **اینجا یک باگ تستی پیدا و اصلاح شد** (بخش ۸) |
| process recreation | PASS | فرآیند تازه همیشه Locked شروع می‌شود؛ DEK در RAM قابل بازیابی نیست |

## ۲. Vault (CRUD + favorite + category) — **PASS**

create/read/edit/delete: تست‌های repo و DAO؛ favorite (ستون متادیتا)، category (فیلتر DB-level + distinct)؛ حذف با تأیید؛ at-rest تضمین‌شده (payload بایتی، بدون plaintext). empty DB، transaction all-or-nothing، dataset بزرگ (۵۰۰۰ ردیف repo / ۲۰۰۰ DAO)، بازگشایی فایل DB پس از «process recreation» — همه PASS (تست‌های موجود + بازاجرا).

## ۳. Search / Filters / Sorting — **PASS**

جستجوی in-memory روی title/username/email/notes (فقط پس از unlock)، فیلتر دسته/علاقه‌مندی DB-level، ۵ ترتیب مرتب‌سازی با tie-break. تست‌های `RoomVaultEntryRepositoryTest` بازاجرا: PASS.

## ۴. Generator — **PASS**

configuration (طول ۸–۶۴، ۴ دسته، حداقل هر دسته، حذف گیج‌کننده، defaults ماندگار در DataStore)، secure generation (SecureRandom + Fisher–Yates + تست seed-determinism مستقل از provider)، constraints (ردّ مجموع حداقل‌ها > طول، دستهٔ خاموش، خارج از محدوده). ۸ تست + تست UI: PASS.

## ۵. Security (crypto/at-rest) — **PASS**

encryption/decryption roundtrip؛ **tag failure** (تگ دستکاری‌شده، AAD mismatch)؛ wrong password؛ corrupted ciphertext؛ **nonce handling** (یکتایی ۱۰۰۰ IV؛ plaintext تکراری → ciphertext متفاوت)؛ key wrapping (wrap/unwrap + خرابی meta/keywrap → VaultDataCorrupted)؛ secret non-persistence (اسکن فایل‌ها + بایت‌های DB)؛ no sensitive logs (صفر Log/println).

## ۶. Backup / Restore — **PASS (FIXED)**

export (راستی‌آزمایی پس از نوشتن)؛ import کامل؛ corruption → CORRUPTED/INTEGRITY_FAILURE؛ invalid format؛ wrong password؛ migration (سفر iteration در فایل، بازکردن با codec متفاوت)؛ **low storage → این فاز FIX شد**: دسته‌بندی IOException به `INSUFFICIENT_STORAGE` (با بررسی suppressed causes) به تابع خالص قابل‌تست منتقل و ۴ تست ViewModel-level جدید اضافه شد (canceled/low-storage/io/corrupted-output/happy)؛ **canceled operation** (لغو picker) → پیام CANCELED.

## ۷. Settings — **PASS**

persistence همهٔ دسته‌ها روی DataStore واقعی، reset، تم (چرخه + ماندگاری بین instance + بازگشت پس از reset)، timeoutها (auto-lock با گزینهٔ «بلافاصله» و ممنوعیت «هرگز»؛ clipboard تا «هرگز») — ۹ تست + تست UI: PASS.

## ۸. Issues پیدا و اصلاح‌شده در این فاز — **FIXED**

1. **[TEST] سناریوی restart برای auth وجود نداشت** → `AuthenticationLifecycleTest` اضافه شد. دو اشکال حین نگارش (keystore تقلبی in-memory که باید مانند Keystore واقعی ماندگار می‌بود؛ reset شدن شمارندهٔ backoff پس از لاگین موفق) با RCA و بازطراحی تست حل شد.
2. **[UX/detekt] پارامتر مردهٔ `themeMode` در `VaultListContent`** (باقیماندهٔ حذف دکمهٔ تم در فاز ۶) → پارامتر حذف و ۴ فراخوان (Search/Favorites/Screen/تست) به‌روز شد؛ detekt مجدد: ۰.
3. **[RELEASE] R8/shrink فعال نبود** → `isMinifyEnabled + isShrinkResources + proguard-android-optimize` با قوانین حداقلی امن (کد بدون reflection) فعال شد؛ `assembleRelease` با `minifyReleaseWithR8` موفق؛ APK: ۳.۱۰MB در برابر ۳۳.۷MB دیباگ. خطاب مشکل R8: کد فقط serializerهای تولیدی serialization و consumer rules Room را استفاده می‌کند؛ نگهداشت خطوط stack trace برای گزارش خرابی خوانا افزوده شد.
4. **[TEST] دسته‌بندی خطای I/O در گیت‌وِی SAF تست‌ناپذیر بود** → `classifyIoException` به companion خالص منتقل و جدول طبقه‌بندی (فضا/عادی/suppressed) تست شد.

## ۹. UI/UX Review — **PASS**

RTL (صفر API مطلق؛ تست‌ها با locale fa-rIR)، dark/light (توکن‌های M3؛ صفر رنگ hardcoded بیرون theme layer)، font scaling (۴ سطح از ریشه)، حالت‌های loading/empty/error در همهٔ صفحه‌ها، تأیید برای هر اقدام مخرب (حذف/جایگزینی/بازنشانی/دورانداختن)، a11y (descriptionها، headings، touch targets ۴۸dp، reduced motion، state بدون اتکا به رنگ).

## ۱۰. Performance Review — **PASS (با یادداشت)**

- **Startup:** ساخت AppContainer غیرمسدودکننده است (Room lazy-open؛ `securityRepository.initialize` غیرهمزمان)؛ هیچ I/O مains-thread ای (اسکن: صفر runBlocking/Thread.sleep در main).
- **Memory:** هیچ reference استاتیک به Activity/Context (فقط applicationContext)؛ plaintext در StateFlowهای صفحه‌محور که با lock نابود می‌شوند.
- **Recomposition:** همهٔ لیست‌ها keyed؛ stateIn(WhileSubscribed)؛ collection کاملاً `collectAsStateWithLifecycle` (صفر `collectAsState`).
- **DB:** indices روی favorite/category/updatedAt؛ جستجو in-memory با فیلتر زودهنگام DB؛ large-dataset tests سبز.
- **Battery:** بدون wakelock/service/polling/location (اسکن: هیچ).
- **Dispatchers:** همهٔ I/O در `Dispatchers.IO/Unconfined` تزریقی؛ flows با `flowOn` (۳ مورد در repo).
- **APK size:** release ۳.۱۰MB (R8) — عالی برای یک password manager کامل.
- **یادداشت:** PBKDF2 ۶۰۰k عمداً CPU/gPU-hungry است (برای کند کردن brute-force)؛ روی سخت‌افزار ضعیف setup/unlock تا ~۱ ثانیه — تصمیم امنیتی آگاهانه.

## ۱۱. Platform Coverage — **PASS با REMAINING**

- **minSdk 29 → Android 10+ پشتیبانی می‌شود**؛ targetSdk/compileSdk = 37 (جدیدترین پشتیبانی‌شده).
- APIهای حساس (SAF CreateDocument/OpenDocument، DataStore، FLAG_SECURE، RECEIVER_NOT_EXPORTED) همگی compat-safe برای API ۲۹+ هستند.
- **REMAINING:** اجرای emulator روی APIهای ۱۰–۱۵ در سندباکس ممکن نیست (فقط Robolectric sdk=35/Android 15 روی JDK 17). رفتار مخصوص نسخه‌های میانی (۱۱–۱۴) نیازمند تست دستگاه واقعی است.

## ۱۲. Security Audit جامع — **PASS**

master password (بدون ذخیره‌سازی، validator قوی) · KDF/salt (PBKDF2-SHA256 600k، salt تصادفی ۱۶ب) · key generation/wrapping (DEK تصادفی + wrap با کلید مشتق + مهر Keystore) · Keystore (کلید غیرخروجی) · AES-256-GCM (تگ ۱۲۸) · nonce (تصادفی یکتا) · ciphertext integrity (تگ + checksum + AAD در بکاپ) · database-at-rest (payload رمز) · backup encryption (فاز ۵) · restore validation (strict parser، سقف حجم، AAD) · clipboard (اقدام صریح + auto-clear + بدون monitor) · screenshots/recents (FLAG_SECURE) · logs (صفر) · exception handling (نوع‌دار، بدون راز در پیام) · lifecycle (۵ رویداد قفل) · memory exposure (صفر کردن DEK با قفل) · hardcoded secrets (صفر) · insecure random (صفر java.util.Random) · exported components (فقط MainActivity برای LAUNCHER) · file sharing/URI grants (بدون FileProvider، بدون grant ماندگار) · manifest permissions (**صفر**) · dependency risk (همه stable؛ بدون کتابخانهٔ متروکه) · R8 risks (کد بدون reflection؛ قوانین حداقلی؛ build موفق).

## ۱۳. مستندات — **PASS**

`docs/ARCHITECTURE.md` جدید: architecture، packages، security model، crypto design، DB schema، backup format، restore behavior، build configuration، dependencies، testing strategy، تصمیم‌های مهم، محدودیت‌ها. README به آن ارجاع می‌دهد.

## ۱۴. زنجیرهٔ نهایی فاز ۷ (بعد از اصلاحات)

| گیت | نتیجه |
|------|-------|
| BUILD (assembleDebug) | PASS |
| UNIT TESTS | PASS ۱۵۶/۱۵۶ (پیش از افزودن تست‌های جدید) |
| UI TESTS (Robolectric Compose، fa) | PASS |
| ERROR INSPECTION + RCA + FIX | ۴ مورد FIXED (بخش ۸) |
| LINT | PASS — ۰ خطا |
| STATIC ANALYSIS (detekt) | PASS — ۰ یافته (پس از حذف پارامتر مرده) |
| SECURITY REVIEW | PASS — ۱۲ کنترل مکانیکی + چک‌لیست کامل |
| CLEAN BUILD | PASS (حذف کامل build caches، ساخت از صفر) |
| REBUILD + RETEST | PASS — **۱۶۴/۱۶۴** (شامل ۸ تست جدید) |
| assembleRelease (R8) | PASS — minifyReleaseWithR8 موفق |

## ۱۵. REMAINING (شفاف و بدون پنهان‌کاری)

1. تست emulator/dستگاه واقعی روی Android 10–15 (سندباکس فاقد emulator؛ Robolectric پوشش می‌دهد اما جای تست میدانی را نمی‌گیرد).
2. امضای release (نیازمند keystore کاربر؛ خارج از دامنهٔ سندباکس).
3. صحت‌سنجی runtime خود R8 روی دستگاه (build سبز است؛ smoke-test میدانی توصیه می‌شود).
4. شبیه‌سازی سخت‌افزاری کمبود فضا روی SAF (طبقه‌بندی tested؛ رفتار دستگاه می‌تواند متفاوت پیاده‌سازی شود).

**جمع‌بندی:** هیچ FAIL باقی‌مانده است؛ ۴ مورد FIXED؛ ۴ مورد REMAINING مستندشده که همگی خارج از توان اجرای سندباکس‌اند، نه ایراد کد.
