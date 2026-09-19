# گزارش فاز ۵ — پشتیبان رمزگذاری‌شده، بازیابی امن، DataStore و تنظیمات

**تاریخ:** ۱۶ سپتامبر ۲۰۲۶
**پروژه:** Dezh Pasargad (مدیریت‌کنندهٔ رمز عبور آفلاین)
**پایه:** commit `e1fd183` (پایان فاز ۴)

---

## ۱. پشتیبان‌گیری (encrypted backup)

### قالب فایل (versioned, migration-friendly, مستقل از DB)

فایل پشتیبان یک **JSON envelope** خالص است — هیچ وابستگی‌ای به فرمت دیتابیس اندروید (SQLite/Room) ندارد:

```
{
  "format": "dezh-backup", "version": 1,
  "createdAtEpochMs": ..., "entryCount": N,
  "kdf":    { "algo": "PBKDF2WithHmacSHA256", "iterations": 600000, "salt": <b64> },
  "cipher": { "algo": "AES-256-GCM", "iv": <b64> },
  "ciphertextSha256": <b64>,     ← checksum یکپارچگی روی ciphertext
  "ciphertext": <b64>            ← AES-256-GCM روی payload سخت‌گیر JSON
}
```

- payload داخل ciphertext: فهرست کامل رکوردها (id، عنوان، username، email، رمز، یادداشت، دسته، علاقه‌مندی، timestamps).
- **رمزگذاری + احراز:** AES-256-GCM با کلید ۲۵۶ بیتی مشتق‌شده از عبارت عبورِ پشتیبان (PBKDF2-HMAC-SHA256، ۶۰۰هزار iteration، salt تصادفی ۱۶ بایتی، IV تصادفی ۱۲ بایتی، تگ ۱۲۸ بیتی).
- **یکپارچگی:** checksum SHA-256 روی ciphertext در هدر + تگ GCM؛ ضمناً `format/version/entryCount/createdAt` به‌عنوان **AAD** به ciphertext قفل شده‌اند — دستکاری هر یک از این فیلدها رمزگشایی را می‌شکند.
- **نسخه‌بندی و مهاجرت:** پارامترهای KDF/رمز داخل خود فایل سفر می‌کنند (تعداد iterationهای متفاوت پذیرفته می‌شود)؛ نسخهٔ بالاتر از پشتیبانی برنامه با خطای متمایز «نسخهٔ پشتیبانی‌نشده» رد می‌شود — مسیر افزودن نسخهٔ ۲ در آینده باز است.
- **هیچ plaintext خروجی نمی‌رود:** تست خودکار تأیید می‌کند رمز عبور، عنوان، ایمیل و username در بایت‌های فایل خروجی صفر است.

### انتخاب محل و نام فایل (SAF)

- خروجی با `ActivityResultContracts.CreateDocument` — کاربر **محل و نام فایل** را آزادانه انتخاب می‌کند؛ نام پیشنهادی از تنظیمات («نام پایهٔ فایل پشتیبان» + تاریخ روز) می‌آید.
- ورودی با `ActivityResultContracts.OpenDocument` — بدون اعتماد به filename یا MIME (پارسر فقط بایت‌ها را می‌بیند و ساختار را از محتوا تشخیص می‌دهد).
- گیت‌وِی فایل (`SafBackupFileGateway`): اعتبارسنجی scheme، سقف ۶۴MB برای ورودی، و دسته‌بندی تمام خطاها به انواع تعریف‌شده (بدون نشت exception خام).

### مدیریت خطاهای خروجی (هر مورد پیام فارسی متمایز)

| خطا | تشخیص |
|------|--------|
| invalid path | scheme نامعتبر یا stream تهی از resolver |
| canceled export | لغو launcher (`uri == null`) |
| insufficient storage | IOException حاوی «space» هنگام نوشتن |
| I/O error | سایر IOExceptionهای نوشتن |
| corrupted output | **راستی‌آزمایی پس از نوشتن**: فایل نوشته‌شده دوباره خوانده و بایت‌به‌بایت + هدرش validate می‌شود |
| partial failure | ناسانی بایت‌های خوانده‌شده با منبع → همان corrupted output |
| unsupported version | کنترل نسخه در هدر |

## ۲. بازیابی (secure restore)

### جریان مرحله‌ای (هیچ گام مخربی خودکار نیست)

1. **انتخاب فایل** → اعتبارسنجی URI + خواندن با سقف حجم.
2. **بررسی هدر (بدون عبارت عبور):** format، نسخه، checksum — کاربر پیش از دادن هر رازی می‌فهمد فایل اصلاً پشتیبان معتبرِ این برنامه است یا نه.
3. **عبارت عبور** → رمزگشایی؛ شکست احراز = «عبارت عبور نادرست» (چون checksum سالم است، خرابی فایل قبلاً جدا شده).
4. **پیش‌نمایش:** تعداد رکوردها.
5. **انتخاب حالت با متن پیامد مشخص:**
   - **ادغام:** «رکوردهای جدید اضافه می‌شوند؛ رکوردهای هم‌شناسه فقط در صورت جدیدتر بودن نسخهٔ پشتیبان به‌روزرسانی می‌شوند. هیچ چیزی حذف نمی‌شود.»
   - **جایگزینی کامل:** «کل گاوصندوق فعلی حذف و با محتوای پشتیبان جایگزین می‌شود. بازگشت‌پذیر نیست.»
   - **انصراف:** در هر مرحله قبل از اعمال.
6. **تأیید صریح برای جایگزینی** (`AlertDialog` با متن مخرب‌بودن) — جایگزینی هرگز بدون این تأیید اجرا نمی‌شود؛ **هیچ overwrite خاموشی وجود ندارد**.

### مدیریت خطاهای ورودی (هر مورد تست‌شده)

valid backup / wrong password / corrupted backup / invalid format / unsupported version / incomplete backup / duplicate entries / integrity failure / import failure — نگاشت یک‌به‌یک `ParseFailure`/`FileFailure` → `RestoreUiError` → پیام فارسی (جدول تست‌ها در §۵).

### امنیت ورودی

- URI فقط با scheme معتبر باز می‌شود؛ به filename/MIME اعتماد نمی‌شود؛ فایل **اجرا** نمی‌شود؛ deserialization فقط با serializerهای خودِ کلاس‌های sealed-backup (kotlinx JSON، `ignoreUnknownKeys=false`، `isLenient=false`، `coerceInputValues=false`) — **هیچ کلاس دلخواهی deserialize نمی‌شود**؛ ورودی ناقص/خراب رد می‌شود؛ payload با `entryCount` هدر مطابقت داده می‌شود (ناقص = INCOMPLETE).
- `duplicate entries` در REPLACE با گارد صریح رد می‌شود (کل گاوصندوق پاک نمی‌شود).

## ۳. تنظیمات (DataStore)

- `DataStoreSettingsRepository` روی **Preferences DataStore** (`androidx.datastore:datastore-preferences:1.2.1`، فایل `dezh_settings.preferences_pb`) — **SharedPreferences به‌طور کامل حذف شد** (کنترلر تم فاز ۴ نیز مهاجرت داده شد).
- خواندن سخت‌گیرانه: enum نامعتبر → پیش‌فرض (نه کرش)؛ clamp روی fontScale/timeout/طول مولّد.
- **فهرست کامل تنظیمات:** theme، accent color (۵ پیش‌تنظیم توکن)، font scale (۴ سطح)، list/grid mode، به‌خاطرسپاری فیلترهای جستجو (آخرین دسته + علاقه‌مندی) + ترتیب مرتب‌سازی، صفحهٔ شروع (۵ گزینه)، دستهٔ پیش‌فرض رکورد جدید، نام پایهٔ فایل پشتیبان، timeout کلیپ‌بورد (۵ گزینه + هرگز، با پاک‌سازی خودکار واقعی کلیپ‌بورد در صفحهٔ جزئیات)، timeout قفل خودکار (۵ گزینه، متصل به `AutoLockController` با ساعتِ زنده)، پیش‌فرض‌های مولّد (طول/دسته‌ها/حداقل‌ها/حذف گیج‌کننده‌ها — write-through از صفحهٔ مولّد)، تأییدیه‌های حذف/دورانداختن، پویانمایی، و **بازنشانی کامل تنظیمات** با تأیید.
- **قرارداد حساس‌نبودن:** اسکن کد تأیید می‌کند هیچ کلید/مقدار عبارت عبور یا secret ای در DataStore نیست؛ کلیدها فقط enum/عدد/bool/رشتهٔ غیرراز هستند. backup passphrase فقط در حافظهٔ جریان عملیات زندگی می‌کند.

## ۴. صفحه‌های جدید (۶ صفحه + wire)

Settings (هاب) / Theme settings / Security settings / Backup / Restore / About — همه از طریق Navigation 3 با مقصدهای parameterless (هیچ secret ای در navigation arguments نیست). About دقیقاً شامل: «دژ» نمایانگر یک دژ محافظ برای اسرار کاربر است، «پاسارگاد» به پاسارگاد و اهمیت تاریخی فارسی/هخامنشی آن اشاره دارد، و Created by Korosh.

## ۵. سناریوهای واقعاً تست‌شده (خلاصهٔ الزامی گزارش)

**BACKUP/RESTORE TESTS — ۲۵ تست، همه سبز:**

`BackupCodecTest` (۱۳):
1. round-trip کامل seal→open با payload فارسی/یونیکد؛
2. خواندن هدر **بدون** عبارت عبور (نسخه/تعداد/تاریخ)؛
3. عبارت عبور نادرست → `WRONG_PASSWORD`؛
4. دستکاری ciphertext → `INTEGRITY_FAILURE` (checksum)؛
5. دستکاری version → `UNSUPPORTED_VERSION` (پیش از رمزگشایی)؛
6. دستکاری entryCount → هدر از آن عبور می‌کند (عمدی: checksum بی‌کلید است) ولی **AAD binding** رمزگشایی را می‌شکند؛
7. ورودی non-JSON → `INVALID_FORMAT`؛ ۸. فایل تهی → `INVALID_FORMAT`؛ ۹. format خارجی → `INVALID_FORMAT`؛
10. ناهماهنگی count → `INCOMPLETE` (با seam `sealWithDeclaredCount`)؛
11. **عدم حضور plaintext credentials در بایت‌های خروجی**؛
12. سفر iteration در فایل (migration-friendliness) + بازکردن با codec دارای پیش‌فرض متفاوت؛
13. salt تصادفی → ciphertext متفاوت (شبه‌تصادف بودن خروجی).

`BackupManagerTest` (۷) — سناریوهای انتها-به-انتها روی repo واقعی با payloadهای رمزنگاری‌شدهٔ session:
1. **export → restore(REPLACE) روی گاوصندوق پُر** — ids/timestamps/secrets دقیقاً برابر؛
2. MERGE با نسخهٔ پشتیبانِ جدیدتر → update (۱ به‌روزرسانی)؛
3. MERGE با نسخهٔ محلیِ جدیدتر → skip + درج رکورد فقط-پشتیبان + دست‌نخوردگی رکورد فقط-محلی (insert=1/update=0/skip=1)؛
4. MERGE پس از حذف محلی → درج مجدد از پشتیبان؛
5. REPLACE با idهای تکراری → رد کامل، گاوصندوق دست‌نخورده؛
6. عبارت عبور نادرست وسط restore → `WRONG_PASSWORD` بدون هیچ تغییر در گاوصندوق؛
7. پشتیبان گاوصندوق خالی → بازیابی = گاوصندوق خالی.

`RestorePlannerTest` (۴): درج غایب‌ها، به‌روزرسانی فقط «اکیداً جدیدتر» (مساوی/قدیمی‌تر skip)، هرگز بدون plan حذف، و رد REPLACE با id تکراری.

**SETTINGS TESTS — ۱۳ تست، همه سبز:** پیش‌فرض‌ها، خواندن/نوشتن همهٔ دسته‌ها، fallback امن، reset، انتخابگرهای گزینه‌ای، چرخهٔ تم + ماندگاری بین instanceها + بازگشت پس از reset (کنترلر تم)، و تست UI صفحهٔ تنظیمات/تم (Robolectric، locale فارسی، DataStore واقعی).

**گیت‌های نهایی:**

| گیت | نتیجه |
|------|-------|
| BUILD (`assembleDebug`) | ✅ EXIT=0 |
| UNIT TESTS (کل سوئیت) | ✅ **۱۴۴/۱۴۴** (۱۰۶ فاز ۴ + ۳۸ جدید) |
| BACKUP/RESTORE TESTS | ✅ ۲۵/۲۵ |
| SETTINGS TESTS | ✅ ۱۳/۱۳ |
| LINT (`lintDebug`) | ✅ ۰ خطا / ۹ اطلاع‌رسانی عمدی (PluralsCandidate برای رشته‌های `%1$d` فارسی/انگلیسی؛ پیشنهاد Gradle 9.7.1؛ توضیح A12+-only بودن dataExtractionRules) + detekt: ۰ یافته |
| SECURITY REVIEW | ✅ ۸ کنترل اسکن‌شده (فوق) — passphrase در DataStore نیست، لاگ صفر، strict parser، AAD، تأیید مخرب، SAVER بکاپ (allowBackup=false + dataExtractionRules) شامل فایل DataStore |

## ۶. یادداشت‌های صادقانه

- تشخیص «فضای کافی نیست» از متن IOException در SAF است؛ اندروید API قطعی برای فضای باقیماندهٔ document provider ندارد — رفتار در شبیه‌ساز/دستگاه‌های واقعی ممکن است به‌صورت I/O error کلی هم ظاهر شود (هر دو مسیر پیام دارند).
- تست UI دیالوگ بازنشانی در Robolectric به دلیل جداسازی پنجرهٔ `AlertDialog` به سطح ViewModel تعویض شد؛ رفتار پاک‌سازی DataStore همان‌طور که هست تست می‌شود.
- هیچ‌جا ادعای «صددرصد امن» نشده؛ همین جمله در متن About و رشته‌ها لحاظ شده است.
