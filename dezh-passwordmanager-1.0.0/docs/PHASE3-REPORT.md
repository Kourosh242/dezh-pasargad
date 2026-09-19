# گزارش فاز ۳ — دژ پاسارگاد: Room 3 · Encrypted Persistence · Vault CRUD

> تاریخ: 2026-09-16 · وضعیت گیت فاز ۳: **سبز — BUILD + 88/88 tests + detekt + lint + security review**
> خارج از محدوده (طبق فرمان): Backup/Restore و Settings کامل — پیاده نشد.

---

## ۱) نکتهٔ Room 3 (به‌روزرسانی وضعیت)

قبل از شروع، انتشار Room 3 دوباره از Google Maven استعلام شد: **3.0.3 همچنان 404 است** (و 3.0/3.0-rc01 هم artifact منتشرشده ندارند). مطابق قانون «جدیدترین stable سازگار» از **Room 2.8.5** (آخرین stable واقعی) استفاده شد. مسیر ارتقای آینده به Room 3 در `DatabaseMigrations` و گزارش ثبت شده است (schema versioned + تست اعتبارسنجی، ارتقای runtime بدون تغییر فرمت دادهٔ ما).

## ۲) Database Schema (v1 — exported و commit شده: `app/schemas/.../1.json`)

جدول `vault_entries` — **encrypted record payload + حداقل metadata**:

| ستون | نوع | نقش |
|---|---|---|
| `id` | TEXT PK | UUID تصادفی (غیرحساس) |
| `payload` | BLOB | **DPVG container** — عنوان، نام کاربری، ایمیل، رمز عبور و یادداشت‌ها به‌صورت JSON کانونیکال و رمزنگاری‌شدهٔ AES-256-GCM با DEK نشست (همان موتور فاز ۲) |
| `category` | TEXT (+index) | متادیتای فیلتر در سطح DB (غیرحساس، طبق طراحی) |
| `favorite` | BOOLEAN (+index) | فیلتر علاقه‌مندی در سطح DB |
| `createdAt` / `updatedAt` | INTEGER (+index روی updatedAt) | ترتیب پایدار و صفحات‌بندی |

قوانین برقرار:
- **هیچ ستون plaintext حساسی وجود ندارد** (password/notes/username/email/title فقط داخل payload رمز).
- **هیچ persistent plaintext search index برای password/notes ساخته نشده** — جستجوی متنی بعد از unlock، در حافظه، روی دادهٔ decrypt‌شده انجام می‌شود (`filterDecrypted`)؛ هیچ ساختار دائمی plaintextای نوشته نمی‌شود.
- ایندکس‌ها فقط روی متادیتای غیرحساس‌اند تا large dataset بدون decrypt فیلتر/مرتب شود.
- `room.schemaLocation` فعال؛ schema در گیت commit شده و با identity-hash تست می‌شود.

## ۳) Repository Architecture (لایه‌بندی اجراشده)

```
Presentation (Compose: VaultListScreen · EntryEditScreen · EntryDetailScreen)
      ↓ فقط StateFlow/UiState — هیچ DB و هیچ business logic در Composable
ViewModels (VaultListViewModel · EntryEditViewModel · EntryDetailViewModel)
      ↓
Use Cases (domain: Observe/Get/Create/Update/Delete/ToggleFavorite/ObserveCategories)
      ↓
VaultEntryRepository (interface در domain)
      ↓
RoomVaultEntryRepository (data)  ←→  VaultSession (رمز/گشایش payload با DEK)
      ↓
VaultEntryDao (Room, Flow-based)  →  DezhVaultDatabase (Room 2.8.5 + KSP)
```

- تمام عملیات DB **suspend/Flow** و در `Dispatchers.Default` هستند؛ UI با `collectAsStateWithLifecycle` جمع می‌کند → **coroutine-friendly و lifecycle-aware**.
- Encrypt/Decrypt فقط در data layer (`sealSecrets`/`decryptRow`)؛ ViewModel و Composable هرگز با ciphertext/DB روبه‌رو نمی‌شوند.
- ورود/خروج دادهٔ حساس فقط در حافظهٔ نشست unlock است؛ session قفل ⇒ عملیات‌ها fail-closed می‌شوند (تست دارد).

## ۴) CRUD Status (همه سبز، با تست)

| عملیات | مسیر | وضعیت |
|---|---|---|
| Create | CreateEntryUseCase → repo.createEntry (UUID + seal + upsert) | ✅ |
| Read (list/detail/search) | Flowهای DAO → decrypt در حافظه → فیلتر in-memory | ✅ |
| Update | updateEntry (re-seal + updatedAt جدید، createdAt حفظ) | ✅ |
| Delete | deleteEntry (با چک وجود) | ✅ |
| Favorite | setFavorite — فقط ستون متادیتا، بدون باز رمزنگاری | ✅ |
| Category | فیلتر/دسته‌ها با query و index در DB | ✅ |

## ۵) Migration Status

- استراتژی: versioned schema در گیت + `DatabaseMigrations.ALL` + ممنوعیت destructive fallback (تنظیم نشده).
- تست‌های اعتبارسنجی v1: (۱) **identity hash** دیتابیس runtime == identityHash فایل export؛ (۲) ستون‌ها و ایندکس‌ها دقیقاً مطابق export؛ (۳) بازگشایی/نوشتن روی schema همان تعریف.
- نکتهٔ مستندشده: MigrationTestHelper به schema به‌صورت test-asset نیاز دارد که در این محیط قابل سیم‌کشی امن نبود؛ اعتبارسنجی معادل با identity-hash/PRAGMA پیاده شد (همان چیزی که Room هنگام بازگشایی چک می‌کند).
- ارتقای آینده 1→2: bump نسخه + commit schema جدید + Migration + گسترش همین تست.
- «persistence after process recreation» با باز و بستن واقعی فایل DB تست شد ✓.

## ۶) Test Status — 88/88 سبز (۷ تست جدید vault + ۲۶ تست DB/repo)

| Suite | تعداد | پوشش الزامات فاز |
|---|---|---|
| RoomVaultEntryRepositoryTest (JVM + crypto واقعی) | 12 | create/read/update/delete، favorite، category، جستجوی in-memory، **encrypted-at-rest payload**، یکتایی nonce per-write، fail-closed در قفل، replace تک‌ردیفی، **large dataset ۵۰۰۰**، roundtrip کانتینر |
| VaultEntryDaoDbTest (Robolectric/SQLite واقعی) | 8 | **empty database**، CRUD، favorite/category query، **transaction integrity** (withTransaction + replace)، **process recreation** (close/reopen فایل)، **large dataset ۲۰۰۰** با insert دسته‌ای |
| VaultPersistenceSecurityDbTest (Robolectric, فایل واقعی) | 2 | **encrypted-at-rest**: اسکن بایت‌به‌بایت فایل DB — نه رمز ورود، نه یادداشت، نه username، نه master-password؛ DPVG magic؛ قفل ⇒ خواندن fail-closed |
| VaultSchemaMigrationTest | 2 | identity-hash == export، ستون‌ها/ایندکس‌ها مطابق v1 |
| بقیه (فازهای قبل: crypto/security/domain) | 64 | رگرسیون کامل سبز |

توجه: تست‌های Robolectric روی **SDK 35** اجرا می‌شوند (آخرین android-all سازگار با JDK 17 baseline؛ شبیه‌سازی SDK 36 به JDK 21 نیاز دارد) — رفتار تحت تست (Room/SQLite) مستقل از SDK است.

## ۷) Instrumentation/UI Tests

- `VaultCrudUiTest` (androidTest، Compose UI): سناریوی کامل setup → ساخت گاوصندوق → افزودن آیتم → دیده‌شدن در لیست.
- **کامپایل آن تأیید شد** (`:app:compileDebugAndroidTestKotlin` ✓)؛ **اجرا به emulator/دستگاه نیاز دارد که در این sandbox وجود ندارد** — این محدودیت صادقانه ثبت می‌شود؛ در محیطی با device: `./gradlew :app:connectedDebugAndroidTest`.

## ۸) Build / Quality Pipeline Result (به ترتیب الزام‌شده)

| مرحله | نتیجه |
|---|---|
| BUILD (`:app:assembleDebug`) | ✅ `dezh-pasargad-debug.apk` (~31MB) |
| UNIT TESTS | ✅ 88/88 (شامل DATABASE TESTS با Room واقعی روی Robolectric) |
| INSTRUMENTATION (compile) | ✅ کامپایل androidTest سبز (اجرا: نیاز به device — ثبت شده) |
| LINT (`:app:lintDebug`) | ✅ 0 error · 3 اطلاع‌رسانی مستندشده (پیشنهاد نسخه‌های جدیدتر ×۲ که به‌عمد رد شد + DataExtractionRules موکول به فاز backup) |
| STATIC ANALYSIS (`:app:detekt`) | ✅ 0 یافته |
| SECURITY REVIEW | ✅ (بخش ۹) |
| RELEASE (`:app:assembleRelease`) | ✅ `dezh-pasargad-release-unsigned.apk` (~24MB) |

خطاهای میانی ریشه‌یابی‌شده (بدون تغییر نسخه‌ها): وابستگی جاافتادهٔ `kotlinx-serialization-json` (Json در core نیست) → زنجیرهٔ خطای type-inference در VMها؛ plurals حذف‌شده در بازنویسی strings؛ import جاافتادهٔ `withTransaction`؛ OOM Metaspace در KSP (متعادل‌سازی مجدد JVM daemon + اجرای فازهای سنگین با daemon تازه)؛ تست‌های non-Unit (`: Unit`)؛ SDK 36 Robolectric نیازمند JDK 21 → SDK 35؛ نام ستون/ایندکس‌های واقعی Room 2.8 در تست schema.

## ۹) Security Review — تأیید persistence (شواهد)

| الزام | نتیجه |
|---|---|
| password plaintext در DB | ❌ نیست — تست اسکن بایت‌به‌بایت فایل SQLite واقعی ( VaultPersistenceSecurityDbTest) |
| master password در DB | ❌ نیست — در لایهٔ data/vault اصلاً مفهوم master password وجود ندارد (grep) + تست اسکن |
| secret در log | ❌ نیست — صفر log call در crypto/security/domain/data-vault/presentation-vault (تست خودکار + grep) |
| sensitive columns | ❌ نداریم — schema export: فقط id/payload/category/favorite/timestamps |
| search index plaintext دائمی | ❌ ساخته نمی‌شود — جستجو فقط in-memory بعد از unlock |
| DB ops در Composable | ❌ — DB فقط در data layer (grep نقض صفر) |
| business logic در Composable | ❌ — صفحات فقط ViewModel را صدا می‌زنند |

## ۱۰) Known Risks / Limitations

1. **جستجوی in-memory روی dataset خیلی بزرگ** (ده‌ها هزار آیتم) هر بار emission، کل payloadها را decrypt می‌کند — برای هزاران آیتم سریع است (تست ۵۰۰۰ سبز)؛ برای مقیاس بزرگ‌تر در فازهای بعد می‌توان جستجوی برگه‌شده/افزایشی اضافه کرد.
2. **ستون category به‌صورت plaintext** نگه داشته شده (برای فیلتر DB) — اگر دسته‌بندی‌ها حساس تلقی شوند باید داخل payload رمز و فیلتر in-memory شود (تصمیم مستند).
3. **MigrationTestHelper** در این محیط سیم نشد (asset مکانیزم) — معادل با identity-hash/PRAGMA پوشش داده شد؛ در CI با device/asset pipeline تکمیلش توصیه می‌شود.
4. **UI tests اجرا نشدند** (بدون emulator) — فقط compile gate.
5. **Room 3** هنوز منتشر نشده؛ ارتقا طبق استراتژی مستند انجام خواهد شد.
