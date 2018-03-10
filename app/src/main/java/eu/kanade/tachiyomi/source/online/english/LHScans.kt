package eu.kanade.tachiyomi.source.online.english

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class LHScans : ParsedHttpSource() {

    override val id: Long = 11

    override val name = "LHScans"

    override val baseUrl = "http://lhscans.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&ungenre=&sort=views&sort_type=DESC", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genres = filters.filterIsInstance<Genre>().joinToString("&") { it.id + arrayOf("=", "=in", "=ex")[it.state] }
        return GET("$baseUrl/search/advanced?q=$query&$genres", headers)
//        return GET(null, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&sort=last_update&sort_type=DESC")

    override fun popularMangaSelector() = "div.media"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 > a").first().let {
            manga.setUrlWithoutDomain("/" + it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a:contains(»)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private fun searchGenresNextPageSelector() = popularMangaNextPageSelector()


    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.row").first()
        manga.author = infoElement.select("ul.manga-info li:nth-child(2) small a.btn.btn-xs.btn-info").first().text()
        manga.genre = infoElement.select("ul.manga-info li:nth-child(3) small").first().text()
        manga.status = parseStatus(infoElement.select("ul.manga-info li:nth-child(4) a.btn.btn-xs.btn-success").first().text())
        manga.description = infoElement.select("div.row p").first().text()
        manga.thumbnail_url = infoElement.select("img.thumbnail").first().attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Completed") -> SManga.COMPLETED
        element.contains("Ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = " table.table.table-hover tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("td a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("/" + urlElement.attr("href"))
        chapter.name = urlElement.text()
//        TODO:
//        chapter.date_upload = element.select("div.date").first()?.text()?.let {
//            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it).time
//        } ?: 0
        chapter.date_upload = 0
        return chapter
    }


    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("img.chapter-img").forEach {
            pages.add(Page(pages.size, "/" + it.attr("src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Тэги", genres)
    private class Genre(name: String, val id: String = name.replace(' ', '_')) : Filter.TriState(name)
    private class Status : Filter.Select<String>("Статус", arrayOf("Все", "Перевод завершен", "Выпуск завершен", "Онгоинг", "Новые главы"))
    private class OrderBy : Filter.Sort("Сортировка",
            arrayOf("Дата", "Популярность", "Имя", "Главы"),
            Filter.Sort.Selection(1, false))


    override fun getFilterList() = FilterList(
            Status(),
            OrderBy(),
            GenreList(getGenreList())
    )


    /* [...document.querySelectorAll("li.sidetag > a:nth-child(1)")].map((el,i) =>
    *  { const link=el.getAttribute('href');const id=link.substr(6,link.length);
    *  return `Genre("${id.replace("_", " ")}")` }).join(',\n')
    *  on http://mangachan.me/
    */
    private fun getGenreList() = listOf(
            Genre("18 плюс"),
            Genre("bdsm"),
            Genre("арт"),
            Genre("боевик"),
            Genre("боевые искусства"),
            Genre("вампиры"),
            Genre("веб"),
            Genre("гарем"),
            Genre("гендерная интрига"),
            Genre("героическое фэнтези"),
            Genre("детектив"),
            Genre("дзёсэй"),
            Genre("додзинси"),
            Genre("драма"),
            Genre("игра"),
            Genre("инцест"),
            Genre("искусство"),
            Genre("история"),
            Genre("киберпанк"),
            Genre("кодомо"),
            Genre("комедия"),
            Genre("литРПГ"),
            Genre("махо-сёдзё"),
            Genre("меха"),
            Genre("мистика"),
            Genre("музыка"),
            Genre("научная фантастика"),
            Genre("повседневность"),
            Genre("постапокалиптика"),
            Genre("приключения"),
            Genre("психология"),
            Genre("романтика"),
            Genre("самурайский боевик"),
            Genre("сборник"),
            Genre("сверхъестественное"),
            Genre("сказка"),
            Genre("спорт"),
            Genre("супергерои"),
            Genre("сэйнэн"),
            Genre("сёдзё"),
            Genre("сёдзё-ай"),
            Genre("сёнэн"),
            Genre("сёнэн-ай"),
            Genre("темное фэнтези"),
            Genre("тентакли"),
            Genre("трагедия"),
            Genre("триллер"),
            Genre("ужасы"),
            Genre("фантастика"),
            Genre("фурри"),
            Genre("фэнтези"),
            Genre("школа"),
            Genre("эротика"),
            Genre("юри"),
            Genre("яой"),
            Genre("ёнкома")
    )
}
