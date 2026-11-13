package ir.mohitban.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.components.safeBottomPadding
import ir.mohitban.app.ui.theme.MColors

/** One topic of the user guide: a title and its points (heading, explanation). */
private class HelpTopic(val icon: ImageVector, val title: String, val points: List<Pair<String, String>>)

private val TOPICS = listOf(
    HelpTopic(
        Icons.Outlined.AccountCircle, "شروع کار و اطلاعات فردی",
        listOf(
            "ثبت‌نام اولیه" to "در اولین ورود، نام، کد ملی، شماره‌ی موبایل، تاریخ تولد و تاریخ استخدام، سمت و محل خدمت (استان، شهرستان، پاسگاه) را وارد کنید. کد ملی و موبایل بررسی می‌شوند و تاریخ‌ها جلالی هستند.",
            "درجه‌ی محیط‌بانی" to "بعد از انتخاب تاریخ استخدام، برنامه درجه‌ی شما را پیشنهاد می‌دهد: از «محیط‌بان سوم» شروع می‌شود و هر ۴ سال کامل یک درجه بالاتر می‌رود. اگر درست بود «بله» را بزنید، وگرنه خودتان از فهرست انتخاب کنید.",
            "عکس و امضا" to "عکس پروفایل را با دوربین یا از گالری انتخاب می‌کنید. امضای راننده و سرنشین در فرم سوخت با انگشت کشیده می‌شود و برای دفعات بعد ذخیره می‌ماند.",
            "ویرایش بعدی" to "هر زمان از «تنظیمات ← اطلاعات فردی و محل خدمت» می‌توانید همه‌چیز را عوض کنید. دکمه‌ی ذخیره پایین صفحه ثابت است.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Home, "صفحه‌ی اصلی",
        listOf(
            "کارت پروفایل" to "بالای صفحه نشان یگان حفاظت، نام، درجه، سمت و سابقه‌ی خدمت شما را نشان می‌دهد. زنگ اعلان‌ها کنار آن است.",
            "میانبرها" to "از همین صفحه به شیفت کاری، گشت‌یار، ثبت سند سوخت، سوابق سوخت، گزارش فصلی، وسایل نقلیه و تنظیمات می‌روید. جمع سوخت فصل جاری هم همین‌جا دیده می‌شود.",
            "خروج" to "برای بستن برنامه دوبار پشت‌سرهم دکمه‌ی بازگشت را بزنید.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.LocalGasStation, "ثبت سند سوخت",
        listOf(
            "ثبت وسیله" to "قبل از اولین سند، خودرو یا موتورسیکلت سازمانی را با پلاک ثبت کنید (از «وسایل نقلیه»). موتورسیکلت فقط بنزین می‌خورد؛ گاز CNG مخصوص خودرو است.",
            "پر کردن فرم" to "وسیله، تاریخ سوخت‌گیری، کیلومتر فعلی (آخرین عدد ثبت‌شده نشان داده می‌شود)، نوع سوخت و مقدار را وارد کنید. نرخ از تنظیمات می‌آید و مبلغ کل خودکار حساب می‌شود.",
            "رسید کارتخوان" to "برای بنزین عکس رسید الزامی است. دوربین را روی رسید بگیرید؛ لبه‌ها خودکار تشخیص داده و تصویر صاف می‌شود. برای گاز، رسید اختیاری است.",
            "امضا و تأیید" to "امضای راننده و (اختیاری) سرنشین را بکشید و «ثبت و ذخیره فرم» را بزنید. فرم رسمی A6 به‌صورت PDF و تصویر ساخته می‌شود و می‌توانید آن را بفرستید.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Description, "سوابق و گزارش فصلی",
        listOf(
            "سوابق سوخت" to "همه‌ی ثبت‌های فصل را می‌بینید. روی هر مورد بزنید تا فرم را ببینید، ویرایش کنید، حذف کنید یا تصویر یا PDF آن را بفرستید. ثبت‌های ویرایش‌شده برچسب دارند.",
            "گزارش فصلی" to "گزارش A4 هر فصل (بر پایه‌ی تقویم جلالی) شامل خلاصه‌ی عملکرد، مجموع بنزین و گاز، کیلومتر ابتدا و انتها، نمودار ماهانه و جزئیات هزینه‌هاست. با «ذخیره PDF» فایلش را می‌گیرید.",
            "مهلت گزارش" to "نزدیک پایان هر فصل، در «اعلان‌ها» یادآوری مهلت گزارش دیده می‌شود.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.DirectionsCar, "وسایل نقلیه",
        listOf(
            "افزودن و ویرایش" to "نوع (خودرو یا موتورسیکلت)، مدل، پلاک قرمز و کیلومتر را ثبت کنید. می‌توانید چند وسیله داشته باشید و در هر سند یکی را انتخاب کنید.",
            "حذف" to "حذف وسیله، ثبت‌های قبلی را خراب نمی‌کند؛ مشخصات وسیله همراه هر سند ذخیره شده است.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.CalendarMonth, "شیفت کاری و تقویم",
        listOf(
            "الگوی شیفت" to "تعداد روز کار و روز استراحت را بدهید (۱ تا ۳۰) و اولین روز شیفت را انتخاب کنید؛ الگو به‌صورت تکراری ادامه پیدا می‌کند. همین‌جا می‌بینید امروز شیفت هستید یا استراحت، و چند روز تا پایان شیفت مانده است.",
            "تقویم ماهانه" to "روی هر روز بزنید تا یادداشت و یادآوری بگذارید یا شیفت همان روز را جابه‌جا کنید. «حذف تغییرات دستی» به الگوی اصلی برمی‌گردد.",
            "مناسبت‌ها" to "سالنامه‌ی محیط زیست مناسبت‌های هر ماه را نشان می‌دهد و روز تولد شما هم علامت دارد.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Map, "گشت‌یار: نقشه",
        listOf(
            "نقشه‌ی تمام‌صفحه" to "با یک انگشت جابه‌جا می‌کنید و با دو انگشت (یا دکمه‌های + و −) بزرگ و کوچک. دکمه‌ی همه‌چیز را در دید می‌آورد و دکمه‌ی موقعیت، نقشه را روی شما می‌گذارد.",
            "آیکن موقعیت" to "سبز یعنی موقعیت شما پیدا شده و نقشه دنبال شماست؛ قرمز یعنی موقعیت پیدا نشده یا نقشه را جابه‌جا کرده‌اید. روی آن بزنید تا دوباره روی خودتان بیاید.",
            "موقعیت من" to "بالای نقشه مختصات، دقت، ارتفاع، تعداد ماهواره، استان و شهرستان و نزدیک‌ترین آبادی دیده می‌شود. با زدن روی قالب، بین درجه‌ی اعشاری، درجه‌دقیقه‌ثانیه و UTM عوض می‌کنید. دکمه‌های کپی و ارسال مختصات کنار آن است.",
            "مرز مناطق" to "مرز مناطق تحت مدیریت همیشه روی نقشه است و بالای نقشه سبز (داخل منطقه) یا قرمز (خارج) نشان داده می‌شود.",
            "ثبت نقطه و گزارش" to "انگشت را روی نقشه نگه دارید تا منوی ثبت نقطه یا گزارش شکارچی و آتش‌سوزی باز شود. دکمه‌ی «ثبت نقطه» بالای نقشه هم نقطه را در موقعیت فعلی ثبت می‌کند.",
            "ضبط مسیر گشت" to "دکمه‌ی «شروع ضبط مسیر گشت» پایین نقشه است. هنگام ضبط می‌توانید موقتاً متوقف و دوباره ادامه دهید، و با «پایان» نام مسیر را بنویسید و ذخیره کنید. ضبط با خاموش‌شدن صفحه هم ادامه دارد؛ بعد از گشت آن را پایان دهید تا باتری نرود. GPS گوشی باید روشن باشد.",
            "نقشه‌های شهرستان" to "با دکمه‌ی لایه‌ها نقشه‌ی هر شهرستان را یک بار (با اینترنت) دانلود کنید یا فایل mbtiles وارد کنید. بعد از آن بدون اینترنت کار می‌کند. همه‌ی نقشه‌های دانلودشده با هم روی نقشه می‌مانند و «نمایش» فقط نما را به آن شهرستان می‌برد.",
            "رفتن به مسیرها، نقاط و ابزارها" to "سه دکمه‌ی بالای نقشه شما را به همان زبانه‌ها می‌برد؛ از آنجا با زبانه‌ی «نقشه» برمی‌گردید.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Timeline, "گشت‌یار: مسیرها",
        listOf(
            "فهرست مسیرها" to "مسیرهای ضبط‌شده و واردشده با مسافت و مدت دیده می‌شوند. «نمایش» مسیر را روی نقشه می‌گذارد و «پنهان» برمی‌دارد.",
            "راهنما" to "با «راهنما» برنامه شما را در مسیر یا برگشت به نقطه‌ی شروع هدایت می‌کند و اگر از مسیر دور شوید هشدار می‌دهد.",
            "وارد و خارج کردن" to "فایل دستگاه GPS با فرمت GPX، KML، KMZ، PLT و WPT را وارد کنید. مسیرها را می‌توانید به GPX یا KML خروجی بگیرید یا تغییر نام و حذف کنید.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Place, "گشت‌یار: نقاط",
        listOf(
            "ثبت نقطه" to "چشمه، کمین، لانه یا محل کشف را با موقعیت فعلی یا با وارد کردن مختصات (درجه‌ی اعشاری، درجه‌دقیقه‌ثانیه یا UTM) ثبت کنید و در صورت نیاز توضیح و عکس اضافه کنید.",
            "مسیریابی و ارسال" to "برای هر نقطه «مسیریابی» تا آنجا را راهنمایی می‌کند و «ارسال» مختصات را می‌فرستد. همه‌ی نقاط به GPX یا KML خروجی می‌گیرند.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Build, "گشت‌یار: ابزارها",
        listOf(
            "پیش‌بینی هوا" to "با اینترنت پیش‌بینی هوای محل شما را نشان می‌دهد و بدون اینترنت آخرین پیش‌بینی ذخیره‌شده را با تاریخ و ساعتش. فقط مختصات تقریبی (حدود یک کیلومتر) فرستاده می‌شود.",
            "قطب‌نما و قبله‌نما" to "عقربه‌ی قرمز شمال جغرافیایی است و علامت سبز جهت قبله. گوشی را صاف و دور از فلز نگه دارید.",
            "اندازه‌گیری مساحت و محیط" to "دور محدوده راه بروید و در هر گوشه «افزودن نقطه» را بزنید تا مساحت (متر مربع یا هکتار) و محیط حساب شود.",
            "تبدیل مختصات" to "بین درجه و UTM تبدیل می‌کند و فاصله و جهت تا موقعیت شما را می‌گوید.",
            "طلوع و غروب" to "زمان طلوع، غروب و ظهر شرعی را برای موقعیت شما حساب می‌کند.",
            "چراغ‌قوه و SOS نوری" to "چراغ‌قوه روشن یا خاموش یا با الگوی SOS چشمک می‌زند تا در شب از دور دیده شود.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Sos, "پیامک اضطراری و کد تیم",
        listOf(
            "شماره‌های گیرنده" to "در «ابزارها ← پیامک اضطراری و گزارش» شماره‌ی همکاران را (با ویرگول جدا) ذخیره کنید.",
            "ارسال کمک یا گزارش" to "«درخواست کمک» یا گزارش شکارچی و آتش‌سوزی، پیامی با مختصات آماده می‌کند و برنامه‌ی پیامک باز می‌شود؛ فقط ارسال را بزنید. بدون اینترنت و فقط با آنتن تلفن کار می‌کند.",
            "دریافت هشدار همکاران" to "با «فعال‌سازی دریافت هشدار» پیامک اضطراری همکار برنامه را روی محل او باز می‌کند و روی نقشه چشمک می‌زند. برای باز شدن روی صفحه‌ی قفل اجازه‌ی اعلان تمام‌صفحه را بدهید.",
            "کد تیم" to "در «تنظیمات ← امنیت» همه‌ی همکاران یک کد طولانی یکسان بگذارند. پیامک‌ها امضا می‌شوند و پیامکی که امضای درست نداشته باشد «تأییدنشده» و بدون آلارم نشان داده می‌شود، چون ممکن است جعلی باشد.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Settings, "تنظیمات، پشتیبان و امنیت",
        listOf(
            "نرخ سوخت" to "نرخ سه نوع بنزین و گاز CNG را از «تنظیمات ← نرخ سوخت» تغییر دهید. ثبت‌های قبلی با نرخ خودشان می‌مانند.",
            "پشتیبان‌گیری" to "«ساخت فایل پشتیبان» همه‌ی اطلاعات، امضاها و رسیدها را در یک فایل می‌گذارد. می‌توانید رمز بگذارید؛ رمز قابل بازیابی نیست، پس آن را فراموش نکنید. هر چند وقت یک‌بار پشتیبان بگیرید.",
            "بازیابی" to "«بازیابی از فایل پشتیبان» اطلاعات فعلی را با محتوای فایل جایگزین می‌کند و روی هر گوشی دیگری هم کار می‌کند. اگر فایل رمز دارد، رمز را می‌پرسد.",
            "قفل برنامه" to "ورود با اثر انگشت یا چهره است و در گوشی‌های بدون آن با الگو یا رمز خود گوشی. بعد از بسته شدن برنامه یا یک دقیقه در پس‌زمینه دوباره می‌پرسد. از «تنظیمات ← امنیت» خاموش می‌شود.",
        ),
    ),
    HelpTopic(
        Icons.Outlined.Info, "نکته‌ها و مشکلات رایج",
        listOf(
            "موقعیت پیدا نمی‌شود" to "GPS گوشی را روشن کنید و به برنامه اجازه‌ی موقعیت بدهید. بار اول در فضای باز بمانید؛ ممکن است چند دقیقه طول بکشد.",
            "اینترنت" to "فقط دانلود نقشه‌ی شهرستان و پیش‌بینی هوا اینترنت می‌خواهند. بقیه‌ی برنامه کاملاً آفلاین است.",
            "باتری" to "ضبط مسیر GPS مصرف دارد؛ بعد از گشت آن را پایان دهید.",
            "دکمه‌ها پایین صفحه گم می‌شوند" to "برنامه برای نوار ناوبری و ژست‌های گوشی فاصله می‌گذارد. اگر هنوز جایی زیر نوار رفت، نسخه‌ی گوشی را برای رفع مشکل گزارش دهید.",
        ),
    ),
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("راهنمای استفاده", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Outlined.Lightbulb, MColors.GreenLight, MColors.Green, size = 44.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("محیط‌یار را چطور به کار ببریم؟", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "هر بخش را باز کنید تا توضیح امکانات و روش استفاده‌اش را ببینید.",
                            style = MaterialTheme.typography.labelMedium, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            TOPICS.forEachIndexed { i, t -> HelpCard(t, startOpen = i == 0) }
            Spacer(Modifier.height(24.dp).safeBottomPadding())
        }
    }
}

@Composable
private fun HelpCard(topic: HelpTopic, startOpen: Boolean) {
    var open by remember { mutableStateOf(startOpen) }
    AppCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(topic.icon, MColors.GreenLight, MColors.Green, size = 40.dp)
                Text(
                    topic.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MColors.Muted, modifier = Modifier.size(22.dp))
            }
            if (open) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    topic.points.forEach { (head, body) ->
                        Column {
                            Text(head, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MColors.Green)
                            Text(body, style = MaterialTheme.typography.bodySmall, color = MColors.Ink, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
