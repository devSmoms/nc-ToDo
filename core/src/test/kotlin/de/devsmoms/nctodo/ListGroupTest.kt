package de.devsmoms.nctodo

import de.devsmoms.nctodo.dav.DavXml
import de.devsmoms.nctodo.dav.Ns
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ListGroupTest {

    @Test
    fun setGroupEscapesAndRemoves() {
        val set = DavXml.setListMeta("  Familie & <Freunde>  ", 30)
        assertTrue("<n:group>Familie &amp; &lt;Freunde&gt;</n:group>" in set)
        assertTrue("<n:order>30</n:order>" in set)
        assertFalse("<d:remove>" in set)
        val remove = DavXml.setListMeta(null)
        assertTrue("<d:remove>" in remove && "<n:group/>" in remove && "<d:set>" !in remove)
        val ungroupedWithOrder = DavXml.setListMeta("   ", 10)
        assertTrue("<d:remove>" in ungroupedWithOrder && "<n:order>10</n:order>" in ungroupedWithOrder)
        // Both variants must be well-formed XML.
        listOf(set, remove, ungroupedWithOrder).forEach {
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it.byteInputStream())
        }
        assertTrue("http://devsmoms.de/ns/nctodo" in DavXml.PROPFIND_CALENDARS)
    }

    @Test
    fun readsGroupFromPropfindAndIgnoresRefusedProperty() {
        val xml = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:n="http://devsmoms.de/ns/nctodo">
              <d:response><d:href>/remote.php/dav/calendars/u/a/</d:href>
                <d:propstat><d:prop><n:group>Arbeit</n:group></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response><d:href>/remote.php/dav/calendars/u/b/</d:href>
                <d:propstat><d:prop><n:group/></d:prop><d:status>HTTP/1.1 403 Forbidden</d:status></d:propstat>
              </d:response>
            </d:multistatus>"""
        val r = DavXml.parseMultistatus(xml, "https://cloud.example.de/remote.php/dav/calendars/u/")
        assertEquals("Arbeit", r[0].text(Ns.NCTODO, "group"))
        // A refused PROPPATCH reports the property with 403: it must not count as saved.
        assertNull(r[1].prop(Ns.NCTODO, "group"))
        assertFalse(r.all { it.prop(Ns.NCTODO, "group") != null })
    }
}
