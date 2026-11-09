package app.klean.backend.dav

import app.klean.backend.ApiException
import io.ktor.http.HttpStatusCode
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

const val NS_DAV = "DAV:"
const val NS_CARD = "urn:ietf:params:xml:ns:carddav"
const val NS_CAL = "urn:ietf:params:xml:ns:caldav"
const val NS_CS = "http://calendarserver.org/ns/"

private val PREFIXES = mapOf(NS_DAV to "d", NS_CARD to "card", NS_CAL to "cal", NS_CS to "cs")

/** A qualified XML name. */
data class QName(val ns: String, val local: String) {
    override fun toString() = "{$ns}$local"
}

fun dav(local: String) = QName(NS_DAV, local)
fun card(local: String) = QName(NS_CARD, local)
fun cal(local: String) = QName(NS_CAL, local)
fun cs(local: String) = QName(NS_CS, local)

/** Parses a request body into a namespace-aware DOM with DTDs/external entities disabled (no XXE). */
fun parseXml(body: String): Element {
    val f = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    return try {
        f.newDocumentBuilder().parse(InputSource(StringReader(body))).documentElement
    } catch (e: Exception) {
        throw ApiException(HttpStatusCode.BadRequest, "bad XML")
    }
}

val Element.qname: QName get() = QName(namespaceURI ?: "", localName ?: nodeName)

fun Element.children(): List<Element> {
    val out = ArrayList<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let { out += it }
    return out
}

fun Element.child(name: QName): Element? = children().firstOrNull { it.qname == name }

/** Streaming-free XML builder for multistatus bodies; always declares the four known prefixes on the root. */
class XmlOut {
    private val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    private var extra = 0

    fun open(name: QName, root: Boolean = false, attrs: Map<String, String> = emptyMap()) {
        sb.append('<').append(tag(name))
        if (root) PREFIXES.forEach { (ns, p) -> sb.append(" xmlns:").append(p).append("=\"").append(ns).append('"') }
        attrs.forEach { (k, v) -> sb.append(' ').append(k).append("=\"").append(esc(v)).append('"') }
        nsDecl(name)
        sb.append('>')
    }

    fun close(name: QName) {
        sb.append("</").append(tag(name)).append('>')
    }

    fun empty(name: QName, attrs: Map<String, String> = emptyMap()) {
        sb.append('<').append(tag(name))
        attrs.forEach { (k, v) -> sb.append(' ').append(k).append("=\"").append(esc(v)).append('"') }
        nsDecl(name)
        sb.append("/>")
    }

    fun text(name: QName, value: String) {
        open(name); sb.append(escText(value)); close(name)
    }

    fun el(name: QName, body: XmlOut.() -> Unit) {
        open(name); body(); close(name)
    }

    fun href(path: String) = text(dav("href"), path)

    private val unknownPrefixes = HashMap<String, String>()

    private fun tag(name: QName): String {
        val p = PREFIXES[name.ns] ?: unknownPrefixes.getOrPut(name.ns) { "x${extra++}" }
        return "$p:${name.local}"
    }

    private fun nsDecl(name: QName) {
        if (name.ns !in PREFIXES) sb.append(" xmlns:").append(unknownPrefixes[name.ns]).append("=\"").append(esc(name.ns)).append('"')
    }

    override fun toString() = sb.toString()

    companion object {
        fun escText(s: String): String = buildString {
            for (c in s) when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '\r' -> append("&#13;")
                else -> append(c)
            }
        }

        fun esc(s: String): String = buildString {
            for (c in s) when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }
}
