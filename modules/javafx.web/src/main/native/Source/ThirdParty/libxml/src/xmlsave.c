/*
 * xmlsave.c: Implementation of the document serializer
 *
 * See Copyright for the status of this software.
 *
 * daniel@veillard.com
 */

#define IN_LIBXML
#include "libxml.h"

#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <libxml/xmlmemory.h>
#include <libxml/parserInternals.h>
#include <libxml/tree.h>
#include <libxml/xmlsave.h>

#define MAX_INDENT 60

#include <libxml/HTMLtree.h>

#include "private/buf.h"
#include "private/enc.h"
#include "private/error.h"
#include "private/io.h"
#include "private/save.h"

#ifdef LIBXML_OUTPUT_ENABLED

#define XHTML_NS_NAME BAD_CAST "http://www.w3.org/1999/xhtml"

struct _xmlSaveCtxt {
    void *_private;
    int type;
    int fd;
    const xmlChar *filename;
    const xmlChar *encoding;
    xmlCharEncodingHandlerPtr handler;
    xmlOutputBufferPtr buf;
    int options;
    int level;
    int format;
    char indent[MAX_INDENT + 1];    /* array for indenting output */
    int indent_nr;
    int indent_size;
    xmlCharEncodingOutputFunc escape;    /* used for element content */
    xmlCharEncodingOutputFunc escapeAttr;/* used for attribute content */
};

/************************************************************************
 *                                    *
 *            Output error handlers                *
 *                                    *
 ************************************************************************/
/**
 * xmlSaveErrMemory:
 * @extra:  extra information
 *
 * Handle an out of memory condition
 */
static void
xmlSaveErrMemory(xmlOutputBufferPtr out)
{
    if (out != NULL)
        out->error = XML_ERR_NO_MEMORY;
    xmlRaiseMemoryError(NULL, NULL, NULL, XML_FROM_OUTPUT, NULL);
}

/**
 * xmlSaveErr:
 * @code:  the error number
 * @node:  the location of the error.
 * @extra:  extra information
 *
 * Handle an out of memory condition
 */
static void
xmlSaveErr(xmlOutputBufferPtr out, int code, xmlNodePtr node,
           const char *extra)
{
    const char *msg = NULL;
    int res;

    /* Don't overwrite memory errors */
    if ((out != NULL) && (out->error == XML_ERR_NO_MEMORY))
        return;

    if (code == XML_ERR_NO_MEMORY) {
        xmlSaveErrMemory(out);
        return;
    }

    if (out != NULL)
        out->error = code;

    switch(code) {
        case XML_SAVE_NOT_UTF8:
        msg = "string is not in UTF-8\n";
        break;
    case XML_SAVE_CHAR_INVALID:
        msg = "invalid character value\n";
        break;
    case XML_SAVE_UNKNOWN_ENCODING:
        msg = "unknown encoding %s\n";
        break;
    case XML_SAVE_NO_DOCTYPE:
        msg = "document has no DOCTYPE\n";
        break;
    default:
        msg = "unexpected error number\n";
    }

    res = __xmlRaiseError(NULL, NULL, NULL, NULL, node,
                          XML_FROM_OUTPUT, code, XML_ERR_ERROR, NULL, 0,
                          extra, NULL, NULL, 0, 0,
                          msg, extra);
    if (res < 0)
        xmlSaveErrMemory(out);
}

/************************************************************************
 *                                    *
 *            Special escaping routines            *
 *                                    *
 ************************************************************************/
static unsigned char *
xmlSerializeHexCharRef(unsigned char *out, int val) {
    unsigned char *ptr;

    *out++ = '&';
    *out++ = '#';
    *out++ = 'x';
    if (val < 0x10) ptr = out;
    else if (val < 0x100) ptr = out + 1;
    else if (val < 0x1000) ptr = out + 2;
    else if (val < 0x10000) ptr = out + 3;
    else if (val < 0x100000) ptr = out + 4;
    else ptr = out + 5;
    out = ptr + 1;
    while (val > 0) {
    switch (val & 0xF) {
        case 0: *ptr-- = '0'; break;
        case 1: *ptr-- = '1'; break;
        case 2: *ptr-- = '2'; break;
        case 3: *ptr-- = '3'; break;
        case 4: *ptr-- = '4'; break;
        case 5: *ptr-- = '5'; break;
        case 6: *ptr-- = '6'; break;
        case 7: *ptr-- = '7'; break;
        case 8: *ptr-- = '8'; break;
        case 9: *ptr-- = '9'; break;
        case 0xA: *ptr-- = 'A'; break;
        case 0xB: *ptr-- = 'B'; break;
        case 0xC: *ptr-- = 'C'; break;
        case 0xD: *ptr-- = 'D'; break;
        case 0xE: *ptr-- = 'E'; break;
        case 0xF: *ptr-- = 'F'; break;
        default: *ptr-- = '0'; break;
    }
    val >>= 4;
    }
    *out++ = ';';
    *out = 0;
    return(out);
}

/**
 * xmlEscapeEntities:
 * @out:  a pointer to an array of bytes to store the result
 * @outlen:  the length of @out
 * @in:  a pointer to an array of unescaped UTF-8 bytes
 * @inlen:  the length of @in
 *
 * Take a block of UTF-8 chars in and escape them. Used when there is no
 * encoding specified.
 *
 * Returns 0 if success, or -1 otherwise
 * The value of @inlen after return is the number of octets consumed
 *     if the return value is positive, else unpredictable.
 * The value of @outlen after return is the number of octets consumed.
 */
static int
xmlEscapeEntities(unsigned char* out, int *outlen,
                 const xmlChar* in, int *inlen) {
    unsigned char* outstart = out;
    const unsigned char* base = in;
    unsigned char* outend = out + *outlen;
    const unsigned char* inend;
    int val;

    inend = in + (*inlen);

    while ((in < inend) && (out < outend)) {
    if (*in == '<') {
        if (outend - out < 4) break;
        *out++ = '&';
        *out++ = 'l';
        *out++ = 't';
        *out++ = ';';
        in++;
        continue;
    } else if (*in == '>') {
        if (outend - out < 4) break;
        *out++ = '&';
        *out++ = 'g';
        *out++ = 't';
        *out++ = ';';
        in++;
        continue;
    } else if (*in == '&') {
        if (outend - out < 5) break;
        *out++ = '&';
        *out++ = 'a';
        *out++ = 'm';
        *out++ = 'p';
        *out++ = ';';
        in++;
        continue;
    } else if (*in == 0xD) {
        if (outend - out < 5) break;
        *out++ = '&';
        *out++ = '#';
        *out++ = 'x';
        *out++ = 'D';
        *out++ = ';';
        in++;
    } else if (((*in >= 0x20) && (*in < 0x80)) ||
               (*in == 0xA) || (*in == 0x9)) {
        /*
         * default case, just copy !
         */
        *out++ = *in++;
        continue;
    } else if (*in < 0x80) {
            /* invalid control char */
        if (outend - out < 8) break;
        out = xmlSerializeHexCharRef(out, 0xFFFD);
        in++;
    } else {
            int len;

        if (outend - out < 11) break;

            len = inend - in;
            val = xmlGetUTF8Char(in, &len);

            if (val < 0) {
                val = 0xFFFD;
                in++;
            } else {
                if (!IS_CHAR(val))
                    val = 0xFFFD;
                in += len;
            }

        /*
         * We could do multiple things here. Just save as a char ref
         */
        out = xmlSerializeHexCharRef(out, val);
    }
    }
    *outlen = out - outstart;
    *inlen = in - base;
    return(0);
}

/************************************************************************
 *                                    *
 *            Allocation and deallocation            *
 *                                    *
 ************************************************************************/
/**
 * xmlSaveCtxtInit:
 * @ctxt: the saving context
 *
 * Initialize a saving context
 */
static void
xmlSaveCtxtInit(xmlSaveCtxtPtr ctxt)
{
    int i;
    int len;

    if (ctxt == NULL) return;
    if ((ctxt->encoding == NULL) && (ctxt->escape == NULL))
        ctxt->escape = xmlEscapeEntities;
    len = xmlStrlen((xmlChar *)xmlTreeIndentString);
    if ((xmlTreeIndentString == NULL) || (len == 0)) {
        memset(&ctxt->indent[0], 0, MAX_INDENT + 1);
    } else {
    ctxt->indent_size = len;
    ctxt->indent_nr = MAX_INDENT / ctxt->indent_size;
    for (i = 0;i < ctxt->indent_nr;i++)
        memcpy(&ctxt->indent[i * ctxt->indent_size], xmlTreeIndentString,
           ctxt->indent_size);
        ctxt->indent[ctxt->indent_nr * ctxt->indent_size] = 0;
    }

    if (xmlSaveNoEmptyTags) {
    ctxt->options |= XML_SAVE_NO_EMPTY;
    }
}

/**
 * xmlFreeSaveCtxt:
 *
 * Free a saving context, destroying the output in any remaining buffer
 */
static void
xmlFreeSaveCtxt(xmlSaveCtxtPtr ctxt)
{
    if (ctxt == NULL) return;
    if (ctxt->encoding != NULL)
        xmlFree((char *) ctxt->encoding);
    if (ctxt->buf != NULL)
        xmlOutputBufferClose(ctxt->buf);
    xmlFree(ctxt);
}

/**
 * xmlNewSaveCtxt:
 *
 * Create a new saving context
 *
 * Returns the new structure or NULL in case of error
 */
static xmlSaveCtxtPtr
xmlNewSaveCtxt(const char *encoding, int options)
{
    xmlSaveCtxtPtr ret;

    ret = (xmlSaveCtxtPtr) xmlMalloc(sizeof(xmlSaveCtxt));
    if (ret == NULL) {
    xmlSaveErrMemory(NULL);
    return ( NULL );
    }
    memset(ret, 0, sizeof(xmlSaveCtxt));

    if (encoding != NULL) {
        int res;

        res = xmlOpenCharEncodingHandler(encoding, /* output */ 1,
                                         &ret->handler);
    if (res != XML_ERR_OK) {
        xmlSaveErr(NULL, res, NULL, encoding);
            xmlFreeSaveCtxt(ret);
        return(NULL);
    }
        ret->encoding = xmlStrdup((const xmlChar *)encoding);
    ret->escape = NULL;
    }
    xmlSaveCtxtInit(ret);

    /*
     * Use the options
     */

    /* Re-check this option as it may already have been set */
    if ((ret->options & XML_SAVE_NO_EMPTY) && ! (options & XML_SAVE_NO_EMPTY)) {
    options |= XML_SAVE_NO_EMPTY;
    }

    ret->options = options;
    if (options & XML_SAVE_FORMAT)
        ret->format = 1;
    else if (options & XML_SAVE_WSNONSIG)
        ret->format = 2;

    return(ret);
}

/************************************************************************
 *                                    *
 *        Dumping XML tree content to a simple buffer        *
 *                                    *
 ************************************************************************/
/**
 * xmlAttrSerializeContent:
 * @buf:  the XML buffer output
 * @doc:  the document
 * @attr:  the attribute pointer
 *
 * Serialize the attribute in the buffer
 */
static void
xmlAttrSerializeContent(xmlOutputBufferPtr buf, xmlAttrPtr attr)
{
    xmlNodePtr children;

    children = attr->children;
    while (children != NULL) {
        switch (children->type) {
            case XML_TEXT_NODE:
            xmlBufAttrSerializeTxtContent(buf, attr->doc,
                                      children->content);
        break;
            case XML_ENTITY_REF_NODE:
                xmlOutputBufferWrite(buf, 1, "&");
                xmlOutputBufferWriteString(buf, (const char *) children->name);
                xmlOutputBufferWrite(buf, 1, ";");
                break;
            default:
                /* should not happen unless we have a badly built tree */
                break;
        }
        children = children->next;
    }
}

/**
 * xmlBufDumpNotationDecl:
 * @buf:  the XML buffer output
 * @nota:  A notation declaration
 *
 * This will dump the content the notation declaration as an XML DTD definition
 */
static void
xmlBufDumpNotationDecl(xmlOutputBufferPtr buf, xmlNotationPtr nota) {
    xmlOutputBufferWrite(buf, 11, "<!NOTATION ");
    xmlOutputBufferWriteString(buf, (const char *) nota->name);

    if (nota->PublicID != NULL) {
    xmlOutputBufferWrite(buf, 8, " PUBLIC ");
    xmlOutputBufferWriteQuotedString(buf, nota->PublicID);
    if (nota->SystemID != NULL) {
        xmlOutputBufferWrite(buf, 1, " ");
        xmlOutputBufferWriteQuotedString(buf, nota->SystemID);
    }
    } else {
    xmlOutputBufferWrite(buf, 8, " SYSTEM ");
    xmlOutputBufferWriteQuotedString(buf, nota->SystemID);
    }

    xmlOutputBufferWrite(buf, 3, " >\n");
}

/**
 * xmlBufDumpNotationDeclScan:
 * @nota:  A notation declaration
 * @buf:  the XML buffer output
 *
 * This is called with the hash scan function, and just reverses args
 */
static void
xmlBufDumpNotationDeclScan(void *nota, void *buf,
                           const xmlChar *name ATTRIBUTE_UNUSED) {
    xmlBufDumpNotationDecl((xmlOutputBufferPtr) buf, (xmlNotationPtr) nota);
}

/**
 * xmlBufDumpNotationTable:
 * @buf:  an xmlBufPtr output
 * @table:  A notation table
 *
 * This will dump the content of the notation table as an XML DTD definition
 */
static void
xmlBufDumpNotationTable(xmlOutputBufferPtr buf, xmlNotationTablePtr table) {
    xmlHashScan(table, xmlBufDumpNotationDeclScan, buf);
}

/**
 * xmlBufDumpElementOccur:
 * @buf:  output buffer
 * @cur:  element table
 *
 * Dump the occurrence operator of an element.
 */
static void
xmlBufDumpElementOccur(xmlOutputBufferPtr buf, xmlElementContentPtr cur) {
    switch (cur->ocur) {
        case XML_ELEMENT_CONTENT_ONCE:
            break;
        case XML_ELEMENT_CONTENT_OPT:
            xmlOutputBufferWrite(buf, 1, "?");
            break;
        case XML_ELEMENT_CONTENT_MULT:
            xmlOutputBufferWrite(buf, 1, "*");
            break;
        case XML_ELEMENT_CONTENT_PLUS:
            xmlOutputBufferWrite(buf, 1, "+");
            break;
    }
}

/**
 * xmlBufDumpElementContent:
 * @buf:  output buffer
 * @content:  element table
 *
 * This will dump the content of the element table as an XML DTD definition
 */
static void
xmlBufDumpElementContent(xmlOutputBufferPtr buf,
                         xmlElementContentPtr content) {
    xmlElementContentPtr cur;

    if (content == NULL) return;

    xmlOutputBufferWrite(buf, 1, "(");
    cur = content;

    do {
        if (cur == NULL) return;

        switch (cur->type) {
            case XML_ELEMENT_CONTENT_PCDATA:
                xmlOutputBufferWrite(buf, 7, "#PCDATA");
                break;
            case XML_ELEMENT_CONTENT_ELEMENT:
                if (cur->prefix != NULL) {
                    xmlOutputBufferWriteString(buf,
                            (const char *) cur->prefix);
                    xmlOutputBufferWrite(buf, 1, ":");
                }
                xmlOutputBufferWriteString(buf, (const char *) cur->name);
                break;
            case XML_ELEMENT_CONTENT_SEQ:
            case XML_ELEMENT_CONTENT_OR:
                if ((cur != content) &&
                    (cur->parent != NULL) &&
                    ((cur->type != cur->parent->type) ||
                     (cur->ocur != XML_ELEMENT_CONTENT_ONCE)))
                    xmlOutputBufferWrite(buf, 1, "(");
                cur = cur->c1;
                continue;
        }

        while (cur != content) {
            xmlElementContentPtr parent = cur->parent;

            if (parent == NULL) return;

            if (((cur->type == XML_ELEMENT_CONTENT_OR) ||
                 (cur->type == XML_ELEMENT_CONTENT_SEQ)) &&
                ((cur->type != parent->type) ||
                 (cur->ocur != XML_ELEMENT_CONTENT_ONCE)))
                xmlOutputBufferWrite(buf, 1, ")");
            xmlBufDumpElementOccur(buf, cur);

            if (cur == parent->c1) {
                if (parent->type == XML_ELEMENT_CONTENT_SEQ)
                    xmlOutputBufferWrite(buf, 3, " , ");
                else if (parent->type == XML_ELEMENT_CONTENT_OR)
                    xmlOutputBufferWrite(buf, 3, " | ");

                cur = parent->c2;
                break;
            }

            cur = parent;
        }
    } while (cur != content);

    xmlOutputBufferWrite(buf, 1, ")");
    xmlBufDumpElementOccur(buf, content);
}

/**
 * xmlBufDumpElementDecl:
 * @buf:  an xmlBufPtr output
 * @elem:  An element table
 *
 * This will dump the content of the element declaration as an XML
 * DTD definition
 */
static void
xmlBufDumpElementDecl(xmlOutputBufferPtr buf, xmlElementPtr elem) {
    xmlOutputBufferWrite(buf, 10, "<!ELEMENT ");
    if (elem->prefix != NULL) {
        xmlOutputBufferWriteString(buf, (const char *) elem->prefix);
        xmlOutputBufferWrite(buf, 1, ":");
    }
    xmlOutputBufferWriteString(buf, (const char *) elem->name);
    xmlOutputBufferWrite(buf, 1, " ");

    switch (elem->etype) {
    case XML_ELEMENT_TYPE_EMPTY:
        xmlOutputBufferWrite(buf, 5, "EMPTY");
        break;
    case XML_ELEMENT_TYPE_ANY:
        xmlOutputBufferWrite(buf, 3, "ANY");
        break;
    case XML_ELEMENT_TYPE_MIXED:
    case XML_ELEMENT_TYPE_ELEMENT:
        xmlBufDumpElementContent(buf, elem->content);
        break;
        default:
            /* assert(0); */
            break;
    }

    xmlOutputBufferWrite(buf, 2, ">\n");
}

/**
 * xmlBufDumpEnumeration:
 * @buf:  output buffer
 * @enum:  An enumeration
 *
 * This will dump the content of the enumeration
 */
static void
xmlBufDumpEnumeration(xmlOutputBufferPtr buf, xmlEnumerationPtr cur) {
    while (cur != NULL) {
        xmlOutputBufferWriteString(buf, (const char *) cur->name);
        if (cur->next != NULL)
            xmlOutputBufferWrite(buf, 3, " | ");

        cur = cur->next;
    }

    xmlOutputBufferWrite(buf, 1, ")");
}
/**
 * xmlBufDumpAttributeDecl:
 * @buf:  output buffer
 * @attr:  An attribute declaration
 *
 * This will dump the content of the attribute declaration as an XML
 * DTD definition
 */
static void
xmlBufDumpAttributeDecl(xmlOutputBufferPtr buf, xmlAttributePtr attr) {
    xmlOutputBufferWrite(buf, 10, "<!ATTLIST ");
    xmlOutputBufferWriteString(buf, (const char *) attr->elem);
    xmlOutputBufferWrite(buf, 1, " ");
    if (attr->prefix != NULL) {
    xmlOutputBufferWriteString(buf, (const char *) attr->prefix);
    xmlOutputBufferWrite(buf, 1, ":");
    }
    xmlOutputBufferWriteString(buf, (const char *) attr->name);

    switch (attr->atype) {
    case XML_ATTRIBUTE_CDATA:
        xmlOutputBufferWrite(buf, 6, " CDATA");
        break;
    case XML_ATTRIBUTE_ID:
        xmlOutputBufferWrite(buf, 3, " ID");
        break;
    case XML_ATTRIBUTE_IDREF:
        xmlOutputBufferWrite(buf, 6, " IDREF");
        break;
    case XML_ATTRIBUTE_IDREFS:
        xmlOutputBufferWrite(buf, 7, " IDREFS");
        break;
    case XML_ATTRIBUTE_ENTITY:
        xmlOutputBufferWrite(buf, 7, " ENTITY");
        break;
    case XML_ATTRIBUTE_ENTITIES:
        xmlOutputBufferWrite(buf, 9, " ENTITIES");
        break;
    case XML_ATTRIBUTE_NMTOKEN:
        xmlOutputBufferWrite(buf, 8, " NMTOKEN");
        break;
    case XML_ATTRIBUTE_NMTOKENS:
        xmlOutputBufferWrite(buf, 9, " NMTOKENS");
        break;
    case XML_ATTRIBUTE_ENUMERATION:
        xmlOutputBufferWrite(buf, 2, " (");
        xmlBufDumpEnumeration(buf, attr->tree);
        break;
    case XML_ATTRIBUTE_NOTATION:
        xmlOutputBufferWrite(buf, 11, " NOTATION (");
        xmlBufDumpEnumeration(buf, attr->tree);
        break;
    default:
            /* assert(0); */
            break;
    }

    switch (attr->def) {
    case XML_ATTRIBUTE_NONE:
        break;
    case XML_ATTRIBUTE_REQUIRED:
        xmlOutputBufferWrite(buf, 10, " #REQUIRED");
        break;
    case XML_ATTRIBUTE_IMPLIED:
        xmlOutputBufferWrite(buf, 9, " #IMPLIED");
        break;
    case XML_ATTRIBUTE_FIXED:
        xmlOutputBufferWrite(buf, 7, " #FIXED");
        break;
    default:
            /* assert(0); */
            break;
    }

    if (attr->defaultValue != NULL) {
    xmlOutputBufferWrite(buf, 1, " ");
    xmlOutputBufferWriteQuotedString(buf, attr->defaultValue);
    }

    xmlOutputBufferWrite(buf, 2, ">\n");
}

/**
 * xmlBufDumpEntityContent:
 * @buf:  output buffer
 * @content:  entity content.
 *
 * This will dump the quoted string value, taking care of the special
 * treatment required by %
 */
static void
xmlBufDumpEntityContent(xmlOutputBufferPtr buf, const xmlChar *content) {
    if (xmlStrchr(content, '%')) {
        const char * base, *cur;

    xmlOutputBufferWrite(buf, 1, "\"");
    base = cur = (const char *) content;
    while (*cur != 0) {
        if (*cur == '"') {
        if (base != cur)
            xmlOutputBufferWrite(buf, cur - base, base);
        xmlOutputBufferWrite(buf, 6, "&quot;");
        cur++;
        base = cur;
        } else if (*cur == '%') {
        if (base != cur)
            xmlOutputBufferWrite(buf, cur - base, base);
        xmlOutputBufferWrite(buf, 6, "&#x25;");
        cur++;
        base = cur;
        } else {
        cur++;
        }
    }
    if (base != cur)
        xmlOutputBufferWrite(buf, cur - base, base);
    xmlOutputBufferWrite(buf, 1, "\"");
    } else {
        xmlOutputBufferWriteQuotedString(buf, content);
    }
}

/**
 * xmlBufDumpEntityDecl:
 * @buf:  an xmlBufPtr output
 * @ent:  An entity table
 *
 * This will dump the content of the entity table as an XML DTD definition
 */
static void
xmlBufDumpEntityDecl(xmlOutputBufferPtr buf, xmlEntityPtr ent) {
    if ((ent->etype == XML_INTERNAL_PARAMETER_ENTITY) ||
        (ent->etype == XML_EXTERNAL_PARAMETER_ENTITY))
        xmlOutputBufferWrite(buf, 11, "<!ENTITY % ");
    else
        xmlOutputBufferWrite(buf, 9, "<!ENTITY ");
    xmlOutputBufferWriteString(buf, (const char *) ent->name);
    xmlOutputBufferWrite(buf, 1, " ");

    if ((ent->etype == XML_EXTERNAL_GENERAL_PARSED_ENTITY) ||
        (ent->etype == XML_EXTERNAL_GENERAL_UNPARSED_ENTITY) ||
        (ent->etype == XML_EXTERNAL_PARAMETER_ENTITY)) {
        if (ent->ExternalID != NULL) {
             xmlOutputBufferWrite(buf, 7, "PUBLIC ");
             xmlOutputBufferWriteQuotedString(buf, ent->ExternalID);
             xmlOutputBufferWrite(buf, 1, " ");
        } else {
             xmlOutputBufferWrite(buf, 7, "SYSTEM ");
        }
        xmlOutputBufferWriteQuotedString(buf, ent->SystemID);
    }

    if (ent->etype == XML_EXTERNAL_GENERAL_UNPARSED_ENTITY) {
        if (ent->content != NULL) { /* Should be true ! */
            xmlOutputBufferWrite(buf, 7, " NDATA ");
            if (ent->orig != NULL)
                xmlOutputBufferWriteString(buf, (const char *) ent->orig);
            else
                xmlOutputBufferWriteString(buf, (const char *) ent->content);
        }
    }

    if ((ent->etype == XML_INTERNAL_GENERAL_ENTITY) ||
        (ent->etype == XML_INTERNAL_PARAMETER_ENTITY)) {
        if (ent->orig != NULL)
            xmlOutputBufferWriteQuotedString(buf, ent->orig);
        else
            xmlBufDumpEntityContent(buf, ent->content);
    }

    xmlOutputBufferWrite(buf, 2, ">\n");
}

/************************************************************************
 *                                    *
 *        Dumping XML tree content to an I/O output buffer    *
 *                                    *
 ************************************************************************/

static int xmlSaveSwitchEncoding(xmlSaveCtxtPtr ctxt, const char *encoding) {
    xmlOutputBufferPtr buf = ctxt->buf;

    if ((encoding != NULL) && (buf->encoder == NULL) && (buf->conv == NULL)) {
        xmlCharEncodingHandler *handler;
        int res;

    res = xmlOpenCharEncodingHandler(encoding, /* output */ 1, &handler);
        if (res != XML_ERR_OK) {
            xmlSaveErr(buf, res, NULL, encoding);
            return(-1);
        }
    buf->conv = xmlBufCreate();
    if (buf->conv == NULL) {
        xmlCharEncCloseFunc(handler);
            xmlSaveErrMemory(buf);
        return(-1);
    }
        buf->encoder = handler;
    /*
     * initialize the state, e.g. if outputting a BOM
     */
        xmlCharEncOutput(buf, 1);
    }
    return(0);
}

static int xmlSaveClearEncoding(xmlSaveCtxtPtr ctxt) {
    xmlOutputBufferPtr buf = ctxt->buf;
    xmlOutputBufferFlush(buf);
    xmlCharEncCloseFunc(buf->encoder);
    xmlBufFree(buf->conv);
    buf->encoder = NULL;
    buf->conv = NULL;
    return(0);
}

#ifdef LIBXML_HTML_ENABLED
static void
xhtmlNodeDumpOutput(xmlSaveCtxtPtr ctxt, xmlNodePtr cur);
#endif
static void xmlNodeDumpOutputInternal(xmlSaveCtxtPtr ctxt, xmlNodePtr cur);
static int xmlDocContentDumpOutput(xmlSaveCtxtPtr ctxt, xmlDocPtr cur);

/**
 * xmlOutputBufferWriteWSNonSig:
 * @ctxt:  The save context
 * @extra: Number of extra indents to apply to ctxt->level
 *
 * Write out formatting for non-significant whitespace output.
 */
static void
xmlOutputBufferWriteWSNonSig(xmlSaveCtxtPtr ctxt, int extra)
{
    int i;
    if ((ctxt == NULL) || (ctxt->buf == NULL))
        return;
    xmlOutputBufferWrite(ctxt->buf, 1, "\n");
    for (i = 0; i < (ctxt->level + extra); i += ctxt->indent_nr) {
        xmlOutputBufferWrite(ctxt->buf, ctxt->indent_size *
                ((ctxt->level + extra - i) > ctxt->indent_nr ?
                 ctxt->indent_nr : (ctxt->level + extra - i)),
                ctxt->indent);
    }
}

/**
 * xmlNsDumpOutput:
 * @buf:  the XML buffer output
 * @cur:  a namespace
 * @ctxt: the output save context. Optional.
 *
 * Dump a local Namespace definition.
 * Should be called in the context of attributes dumps.
 * If @ctxt is supplied, @buf should be its buffer.
 */
static void
xmlNsDumpOutput(xmlOutputBufferPtr buf, xmlDocPtr doc, xmlNsPtr cur,
                xmlSaveCtxtPtr ctxt) {
    if ((cur == NULL) || (buf == NULL)) return;
    if ((cur->type == XML_LOCAL_NAMESPACE) && (cur->href != NULL)) {
    if (xmlStrEqual(cur->prefix, BAD_CAST "xml"))
        return;

    if (ctxt != NULL && ctxt->format == 2)
        xmlOutputBufferWriteWSNonSig(ctxt, 2);
    else
        xmlOutputBufferWrite(buf, 1, " ");

        /* Within the context of an element attributes */
    if (cur->prefix != NULL) {
        xmlOutputBufferWrite(buf, 6, "xmlns:");
        xmlOutputBufferWriteString(buf, (const char *)cur->prefix);
    } else
        xmlOutputBufferWrite(buf, 5, "xmlns");
        xmlOutputBufferWrite(buf, 2, "=\"");
        xmlBufAttrSerializeTxtContent(buf, doc, cur->href);
        xmlOutputBufferWrite(buf, 1, "\"");
    }
}

/**
 * xmlNsListDumpOutputCtxt
 * @ctxt: the save context
 * @cur:  the first namespace
 *
 * Dump a list of local namespace definitions to a save context.
 * Should be called in the context of attribute dumps.
 */
static void
xmlNsListDumpOutputCtxt(xmlSaveCtxtPtr ctxt, xmlDocPtr doc, xmlNsPtr cur) {
    while (cur != NULL) {
        xmlNsDumpOutput(ctxt->buf, doc, cur, ctxt);
    cur = cur->next;
    }
}

/**
 * xmlNsListDumpOutput:
 * @buf:  the XML buffer output
 * @cur:  the first namespace
 *
 * Dump a list of local Namespace definitions.
 * Should be called in the context of attributes dumps.
 */
void
xmlNsListDumpOutput(xmlOutputBufferPtr buf, xmlNsPtr cur) {
    while (cur != NULL) {
        xmlNsDumpOutput(buf, NULL, cur, NULL);
    cur = cur->next;
    }
}

/**
 * xmlDtdDumpOutput:
 * @buf:  the XML buffer output
 * @dtd:  the pointer to the DTD
 *
 * Dump the XML document DTD, if any.
 */
static void
xmlDtdDumpOutput(xmlSaveCtxtPtr ctxt, xmlDtdPtr dtd) {
    xmlOutputBufferPtr buf;
    xmlNodePtr cur;
    int format, level;

    if (dtd == NULL) return;
    if ((ctxt == NULL) || (ctxt->buf == NULL))
        return;
    buf = ctxt->buf;
    xmlOutputBufferWrite(buf, 10, "<!DOCTYPE ");
    xmlOutputBufferWriteString(buf, (const char *)dtd->name);
    if (dtd->ExternalID != NULL) {
    xmlOutputBufferWrite(buf, 8, " PUBLIC ");
    xmlOutputBufferWriteQuotedString(buf, dtd->ExternalID);
    xmlOutputBufferWrite(buf, 1, " ");
    xmlOutputBufferWriteQuotedString(buf, dtd->SystemID);
    }  else if (dtd->SystemID != NULL) {
    xmlOutputBufferWrite(buf, 8, " SYSTEM ");
    xmlOutputBufferWriteQuotedString(buf, dtd->SystemID);
    }
    if ((dtd->entities == NULL) && (dtd->elements == NULL) &&
        (dtd->attributes == NULL) && (dtd->notations == NULL) &&
    (dtd->pentities == NULL)) {
    xmlOutputBufferWrite(buf, 1, ">");
    return;
    }
    xmlOutputBufferWrite(buf, 3, " [\n");
    /*
     * Dump the notations first they are not in the DTD children list
     * Do this only on a standalone DTD or on the internal subset though.
     */
    if ((dtd->notations != NULL) && ((dtd->doc == NULL) ||
        (dtd->doc->intSubset == dtd))) {
        xmlBufDumpNotationTable(buf, (xmlNotationTablePtr) dtd->notations);
    }
    format = ctxt->format;
    level = ctxt->level;
    ctxt->format = 0;
    ctxt->level = -1;
    for (cur = dtd->children; cur != NULL; cur = cur->next) {
        xmlNodeDumpOutputInternal(ctxt, cur);
    }
    ctxt->format = format;
    ctxt->level = level;
    xmlOutputBufferWrite(buf, 2, "]>");
}

/**
 * xmlAttrDumpOutput:
 * @buf:  the XML buffer output
 * @cur:  the attribute pointer
 *
 * Dump an XML attribute
 */
static void
xmlAttrDumpOutput(xmlSaveCtxtPtr ctxt, xmlAttrPtr cur) {
    xmlOutputBufferPtr buf;

    if (cur == NULL) return;
    buf = ctxt->buf;
    if (buf == NULL) return;
    if (ctxt->format == 2)
        xmlOutputBufferWriteWSNonSig(ctxt, 2);
    else
        xmlOutputBufferWrite(buf, 1, " ");
    if ((cur->ns != NULL) && (cur->ns->prefix != NULL)) {
        xmlOutputBufferWriteString(buf, (const char *)cur->ns->prefix);
    xmlOutputBufferWrite(buf, 1, ":");
    }
    xmlOutputBufferWriteString(buf, (const char *)cur->name);
    xmlOutputBufferWrite(buf, 2, "=\"");
#ifdef LIBXML_HTML_ENABLED
    if ((ctxt->options & XML_SAVE_XHTML) &&
        (cur->ns == NULL) &&
        ((cur->children == NULL) ||
         (cur->children->content == NULL) ||
         (cur->children->content[0] == 0)) &&
        (htmlIsBooleanAttr(cur->name))) {
        xmlOutputBufferWriteString(buf, (const char *) cur->name);
    } else
#endif
    {
        xmlAttrSerializeContent(buf, cur);
    }
    xmlOutputBufferWrite(buf, 1, "\"");
}

#ifdef LIBXML_HTML_ENABLED
/**
 * htmlNodeDumpOutputInternal:
 * @cur:  the current node
 *
 * Dump an HTML node, recursive behaviour, children are printed too.
 */
static int
htmlNodeDumpOutputInternal(xmlSaveCtxtPtr ctxt, xmlNodePtr cur) {
    const xmlChar *oldenc = NULL;
    const xmlChar *oldctxtenc = ctxt->encoding;
    const xmlChar *encoding = ctxt->encoding;
    xmlOutputBufferPtr buf = ctxt->buf;
    int switched_encoding = 0;
    xmlDocPtr doc;

    xmlInitParser();

    doc = cur->doc;
    if (doc != NULL) {
        oldenc = doc->encoding;
    if (ctxt->encoding != NULL) {
        doc->encoding = BAD_CAST ctxt->encoding;
    } else if (doc->encoding != NULL) {
        encoding = doc->encoding;
    }
    }

    if ((encoding != NULL) && (doc != NULL))
    htmlSetMetaEncoding(doc, (const xmlChar *) encoding);
    if ((encoding == NULL) && (doc != NULL))
    encoding = htmlGetMetaEncoding(doc);
    if (encoding == NULL)
    encoding = BAD_CAST "HTML";
    if ((encoding != NULL) && (oldctxtenc == NULL) &&
    (buf->encoder == NULL) && (buf->conv == NULL)) {
    if (xmlSaveSwitchEncoding(ctxt, (const char*) encoding) < 0) {
        doc->encoding = oldenc;
        return(-1);
    }
    switched_encoding = 1;
    }
    if (ctxt->options & XML_SAVE_FORMAT)
    htmlNodeDumpFormatOutput(buf, doc, cur,
                       (const char *)encoding, 1);
    else
    htmlNodeDumpFormatOutput(buf, doc, cur,
                       (const char *)encoding, 0);
    /*
     * Restore the state of the saving context at the end of the document
     */
    if ((switched_encoding) && (oldctxtenc == NULL)) {
    xmlSaveClearEncoding(ctxt);
    }
    if (doc != NULL)
    doc->encoding = oldenc;
    return(0);
}
#endif

/**
 * xmlNodeDumpOutputInternal:
 * @cur:  the current node
 *
 * Dump an XML node, recursive behaviour, children are printed too.
 */
static void
xmlNodeDumpOutputInternal(xmlSaveCtxtPtr ctxt, xmlNodePtr cur) {
    int format = ctxt->format;
    xmlNodePtr tmp, root, unformattedNode = NULL, parent;
    xmlAttrPtr attr;
    xmlChar *start, *end;
    xmlOutputBufferPtr buf;

    if (cur == NULL) return;
    buf = ctxt->buf;

    root = cur;
    parent = cur->parent;
    while (1) {
        switch (cur->type) {
        case XML_DOCUMENT_NODE:
        case XML_HTML_DOCUMENT_NODE:
        xmlDocContentDumpOutput(ctxt, (xmlDocPtr) cur);
        break;

        case XML_DTD_NODE:
            xmlDtdDumpOutput(ctxt, (xmlDtdPtr) cur);
            break;

        case XML_DOCUMENT_FRAG_NODE:
            /* Always validate cur->parent when descending. */
            if ((cur->parent == parent) && (cur->children != NULL)) {
                parent = cur;
                cur = cur->children;
                continue;
            }
        break;

        case XML_ELEMENT_DECL:
            xmlBufDumpElementDecl(buf, (xmlElementPtr) cur);
            break;

        case XML_ATTRIBUTE_DECL:
            xmlBufDumpAttributeDecl(buf, (xmlAttributePtr) cur);
            break;

        case XML_ENTITY_DECL:
            xmlBufDumpEntityDecl(buf, (xmlEntityPtr) cur);
            break;

        case XML_ELEMENT_NODE:
        if ((cur != root) && (ctxt->format == 1) &&
                (xmlIndentTreeOutput))
        xmlOutputBufferWrite(buf, ctxt->indent_size *
                     (ctxt->level > ctxt->indent_nr ?
                      ctxt->indent_nr : ctxt->level),
                     ctxt->indent);

            /*
             * Some users like lxml are known to pass nodes with a corrupted
             * tree structure. Fall back to a recursive call to handle this
             * case.
             */
            if ((cur->parent != parent) && (cur->children != NULL)) {
                xmlNodeDumpOutputInternal(ctxt, cur);
                break;
            }

            xmlOutputBufferWrite(buf, 1, "<");
            if ((cur->ns != NULL) && (cur->ns->prefix != NULL)) {
                xmlOutputBufferWriteString(buf, (const char *)cur->ns->prefix);
                xmlOutputBufferWrite(buf, 1, ":");
            }
            xmlOutputBufferWriteString(buf, (const char *)cur->name);
            if (cur->nsDef)
                xmlNsListDumpOutputCtxt(ctxt, cur->doc, cur->nsDef);
            for (attr = cur->properties; attr != NULL; attr = attr->next)
                xmlAttrDumpOutput(ctxt, attr);

            if (cur->children == NULL) {
                if ((ctxt->options & XML_SAVE_NO_EMPTY) == 0) {
                    if (ctxt->format == 2)
                        xmlOutputBufferWriteWSNonSig(ctxt, 0);
                    xmlOutputBufferWrite(buf, 2, "/>");
                } else {
                    if (ctxt->format == 2)
                        xmlOutputBufferWriteWSNonSig(ctxt, 1);
                    xmlOutputBufferWrite(buf, 3, "></");
                    if ((cur->ns != NULL) && (cur->ns->prefix != NULL)) {
                        xmlOutputBufferWriteString(buf,
                                (const char *)cur->ns->prefix);
                        xmlOutputBufferWrite(buf, 1, ":");
                    }
                    xmlOutputBufferWriteString(buf, (const char *)cur->name);
                    if (ctxt->format == 2)
                        xmlOutputBufferWriteWSNonSig(ctxt, 0);
                    xmlOutputBufferWrite(buf, 1, ">");
                }
            } else {
                if (ctxt->format == 1) {
                    tmp = cur->children;
                    while (tmp != NULL) {
                        if ((tmp->type == XML_TEXT_NODE) ||
                            (tmp->type == XML_CDATA_SECTION_NODE) ||
                            (tmp->type == XML_ENTITY_REF_NODE)) {
                            ctxt->format = 0;
                            unformattedNode = cur;
                            break;
                        }
                        tmp = tmp->next;
                    }
                }
                if (ctxt->format == 2)
                    xmlOutputBufferWriteWSNonSig(ctxt, 1);
                xmlOutputBufferWrite(buf, 1, ">");
                if (ctxt->format == 1) xmlOutputBufferWrite(buf, 1, "\n");
                if (ctxt->level >= 0) ctxt->level++;
                parent = cur;
                cur = cur->children;
                continue;
            }

            break;

        case XML_TEXT_NODE:
        if (cur->content == NULL)
                break;
        if (cur->name != xmlStringTextNoenc) {
                xmlOutputBufferWriteEscape(buf, cur->content, ctxt->escape);
        } else {
        /*
         * Disable escaping, needed for XSLT
         */
        xmlOutputBufferWriteString(buf, (const char *) cur->content);
        }
        break;

        case XML_PI_NODE:
        if ((cur != root) && (ctxt->format == 1) && (xmlIndentTreeOutput))
        xmlOutputBufferWrite(buf, ctxt->indent_size *
                     (ctxt->level > ctxt->indent_nr ?
                      ctxt->indent_nr : ctxt->level),
                     ctxt->indent);

            if (cur->content != NULL) {
                xmlOutputBufferWrite(buf, 2, "<?");
                xmlOutputBufferWriteString(buf, (const char *)cur->name);
                if (cur->content != NULL) {
                    if (ctxt->format == 2)
                        xmlOutputBufferWriteWSNonSig(ctxt, 0);
                    else
                        xmlOutputBufferWrite(buf, 1, " ");
                    xmlOutputBufferWriteString(buf,
                            (const char *)cur->content);
                }
                xmlOutputBufferWrite(buf, 2, "?>");
            } else {
                xmlOutputBufferWrite(buf, 2, "<?");
                xmlOutputBufferWriteString(buf, (const char *)cur->name);
                if (ctxt->format == 2)
                    xmlOutputBufferWriteWSNonSig(ctxt, 0);
                xmlOutputBufferWrite(buf, 2, "?>");
            }
            break;

        case XML_COMMENT_NODE:
        if ((cur != root) && (ctxt->format == 1) && (xmlIndentTreeOutput))
        xmlOutputBufferWrite(buf, ctxt->indent_size *
                     (ctxt->level > ctxt->indent_nr ?
                      ctxt->indent_nr : ctxt->level),
                     ctxt->indent);

            if (cur->content != NULL) {
                xmlOutputBufferWrite(buf, 4, "<!--");
                xmlOutputBufferWriteString(buf, (const char *)cur->content);
                xmlOutputBufferWrite(buf, 3, "-->");
            }
            break;

        case XML_ENTITY_REF_NODE:
            xmlOutputBufferWrite(buf, 1, "&");
            xmlOutputBufferWriteString(buf, (const char *)cur->name);
            xmlOutputBufferWrite(buf, 1, ";");
            break;

        case XML_CDATA_SECTION_NODE:
            if (cur->content == NULL || *cur->content == '\0') {
                xmlOutputBufferWrite(buf, 12, "<![CDATA[]]>");
            } else {
                start = end = cur->content;
                while (*end != '\0') {
                    if ((*end == ']') && (*(end + 1) == ']') &&
                        (*(end + 2) == '>')) {
                        end = end + 2;
                        xmlOutputBufferWrite(buf, 9, "<![CDATA[");
                        xmlOutputBufferWrite(buf, end - start,
                                (const char *)start);
                        xmlOutputBufferWrite(buf, 3, "]]>");
                        start = end;
                    }
                    end++;
                }
                if (start != end) {
                    xmlOutputBufferWrite(buf, 9, "<![CDATA[");
                    xmlOutputBufferWriteString(buf, (const char *)start);
                    xmlOutputBufferWrite(buf, 3, "]]>");
                }
            }
            break;

        case XML_ATTRIBUTE_NODE:
            xmlAttrDumpOutput(ctxt, (xmlAttrPtr) cur);
            break;

        case XML_NAMESPACE_DECL:
            xmlNsDumpOutput(buf, NULL, (xmlNsPtr) cur, ctxt);
            break;

        default:
            break;
        }

        while (1) {
            if (cur == root)
                return;
            if ((ctxt->format == 1) &&
                (cur->type != XML_XINCLUDE_START) &&
                (cur->type != XML_XINCLUDE_END))
                xmlOutputBufferWrite(buf, 1, "\n");
            if (cur->next != NULL) {
                cur = cur->next;
                break;
            }

            cur = parent;
            /* cur->parent was validated when descending. */
            parent = cur->parent;

            if (cur->type == XML_ELEMENT_NODE) {
                if (ctxt->level > 0) ctxt->level--;
                if ((xmlIndentTreeOutput) && (ctxt->format == 1))
                    xmlOutputBufferWrite(buf, ctxt->indent_size *
                                         (ctxt->level > ctxt->indent_nr ?
                                          ctxt->indent_nr : ctxt->level),
                                         ctxt->indent);

                xmlOutputBufferWrite(buf, 2, "</");
                if ((cur->ns != NULL) && (cur->ns->prefix != NULL)) {
                    xmlOutputBufferWriteString(buf,
                            (const char *)cur->ns->prefix);
                    xmlOutputBufferWrite(buf, 1, ":");
                }

                xmlOutputBufferWriteString(buf, (const char *)cur->name);
                if (ctxt->format == 2)
                    xmlOutputBufferWriteWSNonSig(ctxt, 0);
                xmlOutputBufferWrite(buf, 1, ">");

                if (cur == unformattedNode) {
                    ctxt->format = format;
                    unformattedNode = NULL;
                }
            }
        }
    }
}

/**
 * xmlDocContentDumpOutput:
 * @cur:  the document
 *
 * Dump an XML document.
 */
static int
xmlDocContentDumpOutput(xmlSaveCtxtPtr ctxt, xmlDocPtr cur) {
#ifdef LIBXML_HTML_ENABLED
    xmlDtdPtr dtd;
    int is_xhtml = 0;
#endif
    const xmlChar *oldenc = cur->encoding;
    const xmlChar *oldctxtenc = ctxt->encoding;
    const xmlChar *encoding = ctxt->encoding;
    xmlCharEncodingOutputFunc oldescape = ctxt->escape;
    xmlCharEncodingOutputFunc oldescapeAttr = ctxt->escapeAttr;
    xmlOutputBufferPtr buf = ctxt->buf;
    xmlCharEncoding enc;
    int switched_encoding = 0;

    xmlInitParser();

    if ((cur->type != XML_HTML_DOCUMENT_NODE) &&
        (cur->type != XML_DOCUMENT_NODE))
     return(-1);

    if (ctxt->encoding != NULL) {
        cur->encoding = BAD_CAST ctxt->encoding;
    } else if (cur->encoding != NULL) {
    encoding = cur->encoding;
    }

    if (((cur->type == XML_HTML_DOCUMENT_NODE) &&
         ((ctxt->options & XML_SAVE_AS_XML) == 0) &&
         ((ctxt->options & XML_SAVE_XHTML) == 0)) ||
        (ctxt->options & XML_SAVE_AS_HTML)) {
#ifdef LIBXML_HTML_ENABLED
        if (encoding != NULL)
        htmlSetMetaEncoding(cur, (const xmlChar *) encoding);
        if (encoding == NULL)
        encoding = htmlGetMetaEncoding(cur);
        if (encoding == NULL)
        encoding = BAD_CAST "HTML";
    if ((encoding != NULL) && (oldctxtenc == NULL) &&
        (buf->encoder == NULL) && (buf->conv == NULL)) {
        if (xmlSaveSwitchEncoding(ctxt, (const char*) encoding) < 0) {
        cur->encoding = oldenc;
        return(-1);
        }
    }
        if (ctxt->options & XML_SAVE_FORMAT)
        htmlDocContentDumpFormatOutput(buf, cur,
                                       (const char *)encoding, 1);
    else
        htmlDocContentDumpFormatOutput(buf, cur,
                                       (const char *)encoding, 0);
    if (ctxt->encoding != NULL)
        cur->encoding = oldenc;
    return(0);
#else
        return(-1);
#endif
    } else if ((cur->type == XML_DOCUMENT_NODE) ||
               (ctxt->options & XML_SAVE_AS_XML) ||
               (ctxt->options & XML_SAVE_XHTML)) {
    enc = xmlParseCharEncoding((const char*) encoding);
    if ((encoding != NULL) && (oldctxtenc == NULL) &&
        (buf->encoder == NULL) && (buf->conv == NULL) &&
        ((ctxt->options & XML_SAVE_NO_DECL) == 0)) {
        if ((enc != XML_CHAR_ENCODING_UTF8) &&
        (enc != XML_CHAR_ENCODING_NONE) &&
        (enc != XML_CHAR_ENCODING_ASCII)) {
        /*
         * we need to switch to this encoding but just for this
         * document since we output the XMLDecl the conversion
         * must be done to not generate not well formed documents.
         */
        if (xmlSaveSwitchEncoding(ctxt, (const char*) encoding) < 0) {
            cur->encoding = oldenc;
            return(-1);
        }
        switched_encoding = 1;
        }
        if (ctxt->escape == xmlEscapeEntities)
        ctxt->escape = NULL;
        if (ctxt->escapeAttr == xmlEscapeEntities)
        ctxt->escapeAttr = NULL;
    }


    /*
     * Save the XML declaration
     */
    if ((ctxt->options & XML_SAVE_NO_DECL) == 0) {
        xmlOutputBufferWrite(buf, 14, "<?xml version=");
        if (cur->version != NULL)
        xmlOutputBufferWriteQuotedString(buf, cur->version);
        else
        xmlOutputBufferWrite(buf, 5, "\"1.0\"");
        if (encoding != NULL) {
        xmlOutputBufferWrite(buf, 10, " encoding=");
        xmlOutputBufferWriteQuotedString(buf, (xmlChar *) encoding);
        }
        switch (cur->standalone) {
        case 0:
            xmlOutputBufferWrite(buf, 16, " standalone=\"no\"");
            break;
        case 1:
            xmlOutputBufferWrite(buf, 17, " standalone=\"yes\"");
            break;
        }
        xmlOutputBufferWrite(buf, 3, "?>\n");
    }

#ifdef LIBXML_HTML_ENABLED
        if (ctxt->options & XML_SAVE_XHTML)
            is_xhtml = 1;
    if ((ctxt->options & XML_SAVE_NO_XHTML) == 0) {
        dtd = xmlGetIntSubset(cur);
        if (dtd != NULL) {
        is_xhtml = xmlIsXHTML(dtd->SystemID, dtd->ExternalID);
        if (is_xhtml < 0) is_xhtml = 0;
        }
    }
#endif
    if (cur->children != NULL) {
        xmlNodePtr child = cur->children;

        while (child != NULL) {
        ctxt->level = 0;
#ifdef LIBXML_HTML_ENABLED
        if (is_xhtml)
            xhtmlNodeDumpOutput(ctxt, child);
        else
#endif
            xmlNodeDumpOutputInternal(ctxt, child);
                if ((child->type != XML_XINCLUDE_START) &&
                    (child->type != XML_XINCLUDE_END))
                    xmlOutputBufferWrite(buf, 1, "\n");
        child = child->next;
        }
    }
    }

    /*
     * Restore the state of the saving context at the end of the document
     */
    if ((switched_encoding) && (oldctxtenc == NULL)) {
    xmlSaveClearEncoding(ctxt);
    ctxt->escape = oldescape;
    ctxt->escapeAttr = oldescapeAttr;
    }
    cur->encoding = oldenc;
    return(0);
}

#ifdef LIBXML_HTML_ENABLED
/************************************************************************
 *                                    *
 *        Functions specific to XHTML serialization        *
 *                                    *
 ************************************************************************/

/**
 * xhtmlIsEmpty:
 * @node:  the node
 *
 * Check if a node is an empty xhtml node
 *
 * Returns 1 if the node is an empty node, 0 if not and -1 in case of error
 */
static int
xhtmlIsEmpty(xmlNodePtr node) {
    if (node == NULL)
    return(-1);
    if (node->type != XML_ELEMENT_NODE)
    return(0);
    if ((node->ns != NULL) && (!xmlStrEqual(node->ns->href, XHTML_NS_NAME)))
    return(0);
    if (node->children != NULL)
    return(0);
    switch (node->name ? node->name[0] : 0) {
    case 'a':
        if (xmlStrEqual(node->name, BAD_CAST "area"))
        return(1);
        return(0);
    case 'b':
        if (xmlStrEqual(node->name, BAD_CAST "br"))
        return(1);
        if (xmlStrEqual(node->name, BAD_CAST "base"))
        return(1);
        if (xmlStrEqual(node->name, BAD_CAST "basefont"))
        return(1);
        return(0);
    case 'c':
        if (xmlStrEqual(node->name, BAD_CAST "col"))
        return(1);
        return(0);
    case 'f':
        if (xmlStrEqual(node->name, BAD_CAST "frame"))
        return(1);
        return(0);
    case 'h':
        if (xmlStrEqual(node->name, BAD_CAST "hr"))
        return(1);
        return(0);
    case 'i':
        if (xmlStrEqual(node->name, BAD_CAST "img"))
        return(1);
        if (xmlStrEqual(node->name, BAD_CAST "input"))
        return(1);
        if (xmlStrEqual(node->name, BAD_CAST "isindex"))
        return(1);
        return(0);
    case 'l':
        if (xmlStrEqual(node->name, BAD_CAST "link"))
        return(1);
        return(0);
    case 'm':
        if (xmlStrEqual(node->name, BAD_CAST "meta"))
        return(1);
        return(0);
    case 'p':
        if (xmlStrEqual(node->name, BAD_CAST "param"))
        return(1);
        return(0);
    }
    return(0);
}

/**
 * xhtmlAttrListDumpOutput:
 * @cur:  the first attribute pointer
 *
 * Dump a list of XML attributes
 */
static void
xhtmlAttrListDumpOutput(xmlSaveCtxtPtr ctxt, xmlAttrPtr cur) {
    xmlAttrPtr xml_lang = NULL;
    xmlAttrPtr lang = NULL;
    xmlAttrPtr name = NULL;
    xmlAttrPtr id = NULL;
    xmlNodePtr parent;
    xmlOutputBufferPtr buf;

    if (cur == NULL) return;
    buf = ctxt->buf;
    parent = cur->parent;
    while (cur != NULL) {
    if ((cur->ns == NULL) && (xmlStrEqual(cur->name, BAD_CAST "id")))
        id = cur;
    else
    if ((cur->ns == NULL) && (xmlStrEqual(cur->name, BAD_CAST "name")))
        name = cur;
    else
    if ((cur->ns == NULL) && (xmlStrEqual(cur->name, BAD_CAST "lang")))
        lang = cur;
    else
    if ((cur->ns != NULL) && (xmlStrEqual(cur->name, BAD_CAST "lang")) &&
        (xmlStrEqual(cur->ns->prefix, BAD_CAST "xml")))
        xml_lang = cur;
        xmlAttrDumpOutput(ctxt, cur);
    cur = cur->next;
    }
    /*
     * C.8
     */
    if ((name != NULL) && (id == NULL)) {
    if ((parent != NULL) && (parent->name != NULL) &&
        ((xmlStrEqual(parent->name, BAD_CAST "a")) ||
         (xmlStrEqual(parent->name, BAD_CAST "p")) ||
         (xmlStrEqual(parent->name, BAD_CAST "div")) ||
         (xmlStrEqual(parent->name, BAD_CAST "img")) ||
         (xmlStrEqual(parent->name, BAD_CAST "map")) ||
         (xmlStrEqual(parent->name, BAD_CAST "applet")) ||
         (xmlStrEqual(parent->name, BAD_CAST "form")) ||
         (xmlStrEqual(parent->name, BAD_CAST "frame")) ||
         (xmlStrEqual(parent->name, BAD_CAST "iframe")))) {
        xmlOutputBufferWrite(buf, 5, " id=\"");
        xmlAttrSerializeContent(buf, name);
        xmlOutputBufferWrite(buf, 1, "\"");
    }
    }
    /*
     * C.7.
     */
    if ((lang != NULL) && (xml_lang == NULL)) {
    xmlOutputBufferWrite(buf, 11, " xml:lang=\"");
    xmlAttrSerializeContent(buf, lang);
    xmlOutputBufferWrite(buf, 1, "\"");
    } else
    if ((xml_lang != NULL) && (lang == NULL)) {
    xmlOutputBufferWrite(buf, 7, " lang=\"");
    xmlAttrSerializeContent(buf, xml_lang);
    xmlOutputBufferWrite(buf, 1, "\"");
    }
}

/**
 * xhtmlNodeDumpOutput:
 * @buf:  the XML buffer output
 * @doc:  the XHTML document
 * @cur:  the current node
 * @level: the imbrication level for indenting
 * @format: is formatting allowed
 * @encoding:  an optional encoding string
 *
 * Dump an XHTML node, recursive behaviour, children are printed too.
 */
static void
xhtmlNodeDumpOutput(xmlSaveCtxtPtr ctxt, xmlNodePtr cur) {
    int format = ctxt->format, addmeta, oldoptions;
    xmlNodePtr tmp, root, unformattedNode = NULL, parent;
    xmlChar *start, *end;
    xmlOutputBufferPtr buf = ctxt->buf;

    if (cur == NULL) return;

    oldoptions = ctxt->options;
    ctxt->options |= XML_SAVE_XHTML;

    root = cur;
    parent = cur->parent;
    while (1) {
        switch (cur->type) {
        case XML_DOCUMENT_NODE:
        case XML_HTML_DOCUMENT_NODE:
            xmlDocContentDumpOutput(ctxt, (xmlDocPtr) cur);
        break;

        case XML_NAMESPACE_DECL:
        xmlNsDumpOutput(buf, NULL, (xmlNsPtr) cur, ctxt);
        break;

        case XML_DTD_NODE:
            xmlDtdDumpOutput(ctxt, (xmlDtdPtr) cur);
        break;

        case XML_DOCUMENT_FRAG_NODE:
            /* Always validate cur->parent when descending. */
            if ((cur->parent == parent) && (cur->children != NULL)) {
                parent = cur;
                cur = cur->children;
                continue;
            }
            break;

        case XML_ELEMENT_DECL:
            xmlBufDumpElementDecl(buf, (xmlElementPtr) cur);
        break;

        case XML_ATTRIBUTE_DECL:
            xmlBufDumpAttributeDecl(buf, (xmlAttributePtr) cur);
        break;

        case XML_ENTITY_DECL:
            xmlBufDumpEntityDecl(buf, (xmlEntityPtr) cur);
        break;

        case XML_ELEMENT_NODE:
            addmeta = 0;

        if ((cur != root) && (ctxt->format == 1) && (xmlIndentTreeOutput))
        xmlOutputBufferWrite(buf, ctxt->indent_size *
                     (ctxt->level > ctxt->indent_nr ?
                      ctxt->indent_nr : ctxt->level),
                     ctxt->indent);

            /*
             * Some users like lxml are known to pass nodes with a corrupted
             * tree structure. Fall back to a recursive call to handle this
             * case.
             */
            if ((cur->parent != parent) && (cur->children != NULL)) {
                xhtmlNodeDumpOutput(ctxt, cur);
                break;
            }

            xmlOutputBufferWrite(buf, 1, "<");
            if ((cur->ns != NULL) && (cur->ns->prefix != NULL)) {
                xmlOutputBufferWriteString(buf, (const char *)cur->ns->prefix);
                xmlOutputBufferWrite(buf, 1, ":");
            }

            xmlOutputBufferWriteString(buf, (const char *)cur->name);
            if (cur->nsDef)
                xmlNsListDumpOutputCtxt(ctxt, cur->doc, cur->nsDef);
            if ((xmlStrEqual(cur->name, BAD_CAST "html") &&
                (cur->ns == NULL) && (cur->nsDef == NULL))) {
                /*
                 * 3.1.1. Strictly Conforming Documents A.3.1.1 3/
                 */
                xmlOutputBufferWriteString(buf,
                        " xmlns=\"http://www.w3.org/1999/xhtml\"");
            }
            if (cur->properties != NULL)
                xhtmlAttrListDumpOutput(ctxt, cur->properties);

            if ((parent != NULL) &&
                (parent->parent == (xmlNodePtr) cur->doc) &&
                xmlStrEqual(cur->name, BAD_CAST"head") &&
                xmlStrEqual(parent->name, BAD_CAST"html")) {

                tmp = cur->children;
                while (tmp != NULL) {
                    if (xmlStrEqual(tmp->name, BAD_CAST"meta")) {
                        xmlChar *httpequiv;

                        httpequiv = xmlGetProp(tmp, BAD_CAST"http-equiv");
                        if (httpequiv != NULL) {
                            if (xmlStrcasecmp(httpequiv,
                                        BAD_CAST"Content-Type") == 0) {
                                xmlFree(httpequiv);
                                break;
                            }
                            xmlFree(httpequiv);
                        }
                    }
                    tmp = tmp->next;
                }
                if (tmp == NULL)
                    addmeta = 1;
            }

            if (cur->children == NULL) {
                if (((cur->ns == NULL) || (cur->ns->prefix == NULL)) &&
                    ((xhtmlIsEmpty(cur) == 1) && (addmeta == 0))) {
                    /*
                     * C.2. Empty Elements
                     */
                    xmlOutputBufferWrite(buf, 3, " />");
                } else {
                    if (addmeta == 1) {
                        xmlOutputBufferWrite(buf, 1, ">");
                        if (ctxt->format == 1) {
                            xmlOutputBufferWrite(buf, 1, "\n");
                            if (xmlIndentTreeOutput)
                                xmlOutputBufferWrite(buf, ctxt->indent_size *
                                    (ctxt->level + 1 > ctxt->indent_nr ?
                                    ctxt->indent_nr : ctxt->level + 1),
                                    ctxt->indent);
                        }
                        xmlOutputBufferWriteString(buf,
                                "<meta http-equiv=\"Content-Type\" "
                                "content=\"text/html; charset=");
                        if (ctxt->encoding) {
                            xmlOutputBufferWriteString(buf,
                                    (const char *)ctxt->encoding);
                        } else {
                            xmlOutputBufferWrite(buf, 5, "UTF-8");
                        }
                        xmlOutputBufferWrite(buf, 4, "\" />");
                        if (ctxt->format == 1)
                            xmlOutputBufferWrite(buf, 1, "\n");
                    } else {
                        xmlOutputBufferWrite(buf, 1, ">");
                    }
                    /*
                     * C.3. Element Minimization and Empty Element Content
                     */
                    xmlOutputBufferWrite(buf, 2, "</");
                    if ((cur->ns != NULL) && (cur->ns->prefix != NULL)) {
                        xmlOutputBufferWriteString(buf,
                                (const char *)cur->ns->prefix);
                        xmlOutputBufferWrite(buf, 1, ":");
                    }
                    xmlOutputBufferWriteString(buf, (const char *)cur->name);
                    xmlOutputBufferWrite(buf, 1, ">");
                }
            } else {
                xmlOutputBufferWrite(buf, 1, ">");
                if (addmeta == 1) {
                    if (ctxt->format == 1) {
                        xmlOutputBufferWrite(buf, 1, "\n");
                        if (xmlIndentTreeOutput)
                            xmlOutputBufferWrite(buf, ctxt->indent_size *
                                (ctxt->level + 1 > ctxt->indent_nr ?
                                ctxt->indent_nr : ctxt->level + 1),
                                ctxt->indent);
                    }
                    xmlOutputBufferWriteString(buf,
                            "<meta http-equiv=\"Content-Type\" "
                            "content=\"text/html; charset=");
                    if (ctxt->encoding) {
                        xmlOutputBufferWriteString(buf,
                                (const char *)ctxt->encoding);
                    } else {
                        xmlOutputBufferWrite(buf, 5, "UTF-8");
                    }
                    xmlOutputBufferWrite(buf, 4, "\" />");
                }

                if (ctxt->format == 1) {
                    tmp = cur->children;
                    while (tmp != NULL) {
                        if ((tmp->type == XML_TEXT_NODE) ||
                            (tmp->type == XML_ENTITY_REF_NODE)) {
                            unformattedNode = cur;
                            ctxt->format = 0;
                            break;
                        }
                        tmp = tmp->next;
                    }
                }

                if (ctxt->format == 1) xmlOutputBufferWrite(buf, 1, "\n");
                if (ctxt->level >= 0) ctxt->level++;
                parent = cur;
                cur = cur->children;
                continue;
            }

            break;

        case XML_TEXT_NODE:
        if (cur->content == NULL)
                break;
        if ((cur->name == xmlStringText) ||
        (cur->name != xmlStringTextNoenc)) {
                xmlOutputBufferWriteEscape(buf, cur->content, ctxt->escape);
        } else {
        /*
         * Disable escaping, needed for XSLT
         */
        xmlOutputBufferWriteString(buf, (const char *) cur->content);
        }
        break;

        case XML_PI_NODE:
            if (cur->content != NULL) {
                xmlOutputBufferWrite(buf, 2, "<?");
                xmlOutputBufferWriteString(buf, (const char *)cur->name);
                if (cur->content != NULL) {
                    xmlOutputBufferWrite(buf, 1, " ");
                    xmlOutputBufferWriteString(buf,
                            (const char *)cur->content);
                }
                xmlOutputBufferWrite(buf, 2, "?>");
            } else {
                xmlOutputBufferWrite(buf, 2, "<?");
                xmlOutputBufferWriteString(buf, (const char *)cur->name);
                xmlOutputBufferWrite(buf, 2, "?>");
            }
            break;

        case XML_COMMENT_NODE:
            if (cur->content != NULL) {
                xmlOutputBufferWrite(buf, 4, "<!--");
                xmlOutputBufferWriteString(buf, (const char *)cur->content);
                xmlOutputBufferWrite(buf, 3, "-->");
            }
            break;

        case XML_ENTITY_REF_NODE:
            xmlOutputBufferWrite(buf, 1, "&");
            xmlOutputBufferWriteString(buf, (const char *)cur->name);
            xmlOutputBufferWrite(buf, 1, ";");
            break;

        case XML_CDATA_SECTION_NODE:
            if (cur->content == NULL || *cur->content == '\0') {
                xmlOutputBufferWrite(buf, 12, "<![CDATA[]]>");
            } else {
                start = end = cur->content;
                while (*end != '\0') {
                    if (*end == ']' && *(end + 1) == ']' &&
                        *(end + 2) == '>') {
                        end = end + 2;
                        xmlOutputBufferWrite(buf, 9, "<![CDATA[");
                        xmlOutputBufferWrite(buf, end - start,
                                (const char *)start);
                        xmlOutputBufferWrite(buf, 3, "]]>");
                        start = end;
                    }
                    end++;
                }
                if (start != end) {
                    xmlOutputBufferWrite(buf, 9, "<![CDATA[");
                    xmlOutputBufferWriteString(buf, (const char *)start);
                    xmlOutputBufferWrite(buf, 3, "]]>");
                }
            }
            break;

        case XML_ATTRIBUTE_NODE:
            xmlAttrDumpOutput(ctxt, (xmlAttrPtr) cur);
        break;

        default:
            break;
        }

        while (1) {
            if (cur == root)
                return;
            if (ctxt->format == 1)
                xmlOutputBufferWrite(buf, 1, "\n");
            if (cur->next != NULL) {
                cur = cur->next;
                break;
            }

            cur = parent;
            /* cur->parent was validated when descending. */
            parent = cur->parent;

            if (cur->type == XML_ELEMENT_NODE) {
                if (ctxt->level > 0) ctxt->level--;
                if ((xmlIndentTreeOutput) && (ctxt->format == 1))
                    xmlOutputBufferWrite(buf, ctxt->indent_size *
                                         (ctxt->level > ctxt->indent_nr ?
                                          ctxt->indent_nr : ctxt->level),
                                         ctxt->indent);

                xmlOutputBufferWrite(buf, 2, "</");
                if ((cur->ns != NULL) && (cur->ns->prefix != NULL)) {
                    xmlOutputBufferWriteString(buf,
                            (const char *)cur->ns->prefix);
                    xmlOutputBufferWrite(buf, 1, ":");
                }

                xmlOutputBufferWriteString(buf, (const char *)cur->name);
                xmlOutputBufferWrite(buf, 1, ">");

                if (cur == unformattedNode) {
                    ctxt->format = format;
                    unformattedNode = NULL;
                }
            }
        }
    }

    ctxt->options = oldoptions;
}
#endif

/************************************************************************
 *                                    *
 *            Public entry points                *
 *                                    *
 ************************************************************************/

/**
 * xmlSaveToFd:
 * @fd:  a file descriptor number
 * @encoding:  the encoding name to use or NULL
 * @options:  a set of xmlSaveOptions
 *
 * Create a document saving context serializing to a file descriptor
 * with the encoding and the options given.
 *
 * Returns a new serialization context or NULL in case of error.
 */
xmlSaveCtxtPtr
xmlSaveToFd(int fd, const char *encoding, int options)
{
    xmlSaveCtxtPtr ret;

    ret = xmlNewSaveCtxt(encoding, options);
    if (ret == NULL) return(NULL);
    ret->buf = xmlOutputBufferCreateFd(fd, ret->handler);
    if (ret->buf == NULL) {
        xmlCharEncCloseFunc(ret->handler);
    xmlFreeSaveCtxt(ret);
    return(NULL);
    }
    return(ret);
}

/**
 * xmlSaveToFilename:
 * @filename:  a file name or an URL
 * @encoding:  the encoding name to use or NULL
 * @options:  a set of xmlSaveOptions
 *
 * Create a document saving context serializing to a filename or possibly
 * to an URL (but this is less reliable) with the encoding and the options
 * given.
 *
 * Returns a new serialization context or NULL in case of error.
 */
xmlSaveCtxtPtr
xmlSaveToFilename(const char *filename, const char *encoding, int options)
{
    xmlSaveCtxtPtr ret;
    int compression = 0; /* TODO handle compression option */

    ret = xmlNewSaveCtxt(encoding, options);
    if (ret == NULL) return(NULL);
    ret->buf = xmlOutputBufferCreateFilename(filename, ret->handler,
                                             compression);
    if (ret->buf == NULL) {
        xmlCharEncCloseFunc(ret->handler);
    xmlFreeSaveCtxt(ret);
    return(NULL);
    }
    return(ret);
}

/**
 * xmlSaveToBuffer:
 * @buffer:  a buffer
 * @encoding:  the encoding name to use or NULL
 * @options:  a set of xmlSaveOptions
 *
 * Create a document saving context serializing to a buffer
 * with the encoding and the options given
 *
 * Returns a new serialization context or NULL in case of error.
 */

xmlSaveCtxtPtr
xmlSaveToBuffer(xmlBufferPtr buffer, const char *encoding, int options)
{
    xmlSaveCtxtPtr ret;

    ret = xmlNewSaveCtxt(encoding, options);
    if (ret == NULL) return(NULL);
    ret->buf = xmlOutputBufferCreateBuffer(buffer, ret->handler);
    if (ret->buf == NULL) {
        xmlCharEncCloseFunc(ret->handler);
    xmlFreeSaveCtxt(ret);
    return(NULL);
    }
    return(ret);
}

/**
 * xmlSaveToIO:
 * @iowrite:  an I/O write function
 * @ioclose:  an I/O close function
 * @ioctx:  an I/O handler
 * @encoding:  the encoding name to use or NULL
 * @options:  a set of xmlSaveOptions
 *
 * Create a document saving context serializing to a file descriptor
 * with the encoding and the options given
 *
 * Returns a new serialization context or NULL in case of error.
 */
xmlSaveCtxtPtr
xmlSaveToIO(xmlOutputWriteCallback iowrite,
            xmlOutputCloseCallback ioclose,
            void *ioctx, const char *encoding, int options)
{
    xmlSaveCtxtPtr ret;

    ret = xmlNewSaveCtxt(encoding, options);
    if (ret == NULL) return(NULL);
    ret->buf = xmlOutputBufferCreateIO(iowrite, ioclose, ioctx, ret->handler);
    if (ret->buf == NULL) {
        xmlCharEncCloseFunc(ret->handler);
    xmlFreeSaveCtxt(ret);
    return(NULL);
    }
    return(ret);
}

/**
 * xmlSaveDoc:
 * @ctxt:  a document saving context
 * @doc:  a document
 *
 * Save a full document to a saving context
 * TODO: The function is not fully implemented yet as it does not return the
 * byte count but 0 instead
 *
 * Returns the number of byte written or -1 in case of error
 */
long
xmlSaveDoc(xmlSaveCtxtPtr ctxt, xmlDocPtr doc)
{
    long ret = 0;

    if ((ctxt == NULL) || (doc == NULL)) return(-1);
    if (xmlDocContentDumpOutput(ctxt, doc) < 0)
        return(-1);
    return(ret);
}

/**
 * xmlSaveTree:
 * @ctxt:  a document saving context
 * @cur:  the top node of the subtree to save
 *
 * Save a subtree starting at the node parameter to a saving context
 * TODO: The function is not fully implemented yet as it does not return the
 * byte count but 0 instead
 *
 * Returns the number of byte written or -1 in case of error
 */
long
xmlSaveTree(xmlSaveCtxtPtr ctxt, xmlNodePtr cur)
{
    long ret = 0;

    if ((ctxt == NULL) || (cur == NULL)) return(-1);
#ifdef LIBXML_HTML_ENABLED
    if (ctxt->options & XML_SAVE_XHTML) {
        xhtmlNodeDumpOutput(ctxt, cur);
        return(ret);
    }
    if (((cur->type != XML_NAMESPACE_DECL) && (cur->doc != NULL) &&
         (cur->doc->type == XML_HTML_DOCUMENT_NODE) &&
         ((ctxt->options & XML_SAVE_AS_XML) == 0)) ||
        (ctxt->options & XML_SAVE_AS_HTML)) {
    htmlNodeDumpOutputInternal(ctxt, cur);
    return(ret);
    }
#endif
    xmlNodeDumpOutputInternal(ctxt, cur);
    return(ret);
}

/**
 * xmlSaveNotationDecl:
 * @ctxt:  save context
 * @cur:  notation
 *
 * Serialize a notation declaration.
 *
 * Return 0 on succes, -1 on error.
 */
int
xmlSaveNotationDecl(xmlSaveCtxtPtr ctxt, xmlNotationPtr cur) {
    if (ctxt == NULL)
        return(-1);
    xmlBufDumpNotationDecl(ctxt->buf, cur);
    return(0);
}

/**
 * xmlSaveNotationTable:
 * @ctxt:  save context
 * @cur:  notation table
 *
 * Serialize notation declarations of a document.
 *
 * Return 0 on succes, -1 on error.
 */
int
xmlSaveNotationTable(xmlSaveCtxtPtr ctxt, xmlNotationTablePtr cur) {
    if (ctxt == NULL)
        return(-1);
    xmlBufDumpNotationTable(ctxt->buf, cur);
    return(0);
}

/**
 * xmlSaveFlush:
 * @ctxt:  a document saving context
 *
 * Flush a document saving context, i.e. make sure that all bytes have
 * been output.
 *
 * Returns the number of byte written or -1 in case of error.
 */
int
xmlSaveFlush(xmlSaveCtxtPtr ctxt)
{
    if (ctxt == NULL) return(-1);
    if (ctxt->buf == NULL) return(-1);
    return(xmlOutputBufferFlush(ctxt->buf));
}

/**
 * xmlSaveClose:
 * @ctxt:  a document saving context
 *
 * Close a document saving context, i.e. make sure that all bytes have
 * been output and free the associated data.
 *
 * Returns the number of byte written or -1 in case of error.
 */
int
xmlSaveClose(xmlSaveCtxtPtr ctxt)
{
    int ret;

    if (ctxt == NULL) return(-1);
    ret = xmlSaveFlush(ctxt);
    xmlFreeSaveCtxt(ctxt);
    return(ret);
}

/**
 * xmlSaveFinish:
 * @ctxt:  a document saving context
 *
 * Close a document saving context, i.e. make sure that all bytes have
 * been output and free the associated data.
 *
 * Available since 2.13.0.
 *
 * Returns an xmlParserErrors code.
 */
int
xmlSaveFinish(xmlSaveCtxtPtr ctxt)
{
    int ret;

    if (ctxt == NULL)
        return(XML_ERR_INTERNAL_ERROR);
    xmlSaveFlush(ctxt);
    ret = ctxt->buf->error;
    xmlFreeSaveCtxt(ctxt);
    return(ret);
}

/**
 * xmlSaveSetEscape:
 * @ctxt:  a document saving context
 * @escape:  the escaping function
 *
 * Set a custom escaping function to be used for text in element content
 *
 * Returns 0 if successful or -1 in case of error.
 */
int
xmlSaveSetEscape(xmlSaveCtxtPtr ctxt, xmlCharEncodingOutputFunc escape)
{
    if (ctxt == NULL) return(-1);
    ctxt->escape = escape;
    return(0);
}

/**
 * xmlSaveSetAttrEscape:
 * @ctxt:  a document saving context
 * @escape:  the escaping function
 *
 * Set a custom escaping function to be used for text in attribute content
 *
 * Returns 0 if successful or -1 in case of error.
 */
int
xmlSaveSetAttrEscape(xmlSaveCtxtPtr ctxt, xmlCharEncodingOutputFunc escape)
{
    if (ctxt == NULL) return(-1);
    ctxt->escapeAttr = escape;
    return(0);
}

/************************************************************************
 *                                    *
 *        Public entry points based on buffers            *
 *                                    *
 ************************************************************************/

/**
 * xmlBufAttrSerializeTxtContent:
 * @buf:  output buffer
 * @doc:  the document
 * @string: the text content
 *
 * Serialize text attribute values to an xmlBufPtr
 */
void
xmlBufAttrSerializeTxtContent(xmlOutputBufferPtr buf, xmlDocPtr doc,
                              const xmlChar *string)
{
    const xmlChar *base, *cur;

    if (string == NULL)
        return;
    base = cur = string;
    while (*cur != 0) {
        if (*cur == '\n') {
            if (base != cur)
                xmlOutputBufferWrite(buf, cur - base, (const char *) base);
            xmlOutputBufferWrite(buf, 5, "&#10;");
            cur++;
            base = cur;
        } else if (*cur == '\r') {
            if (base != cur)
                xmlOutputBufferWrite(buf, cur - base, (const char *) base);
            xmlOutputBufferWrite(buf, 5, "&#13;");
            cur++;
            base = cur;
        } else if (*cur == '\t') {
            if (base != cur)
                xmlOutputBufferWrite(buf, cur - base, (const char *) base);
            xmlOutputBufferWrite(buf, 4, "&#9;");
            cur++;
            base = cur;
        } else if (*cur == '"') {
            if (base != cur)
                xmlOutputBufferWrite(buf, cur - base, (const char *) base);
            xmlOutputBufferWrite(buf, 6, "&quot;");
            cur++;
            base = cur;
        } else if (*cur == '<') {
            if (base != cur)
                xmlOutputBufferWrite(buf, cur - base, (const char *) base);
            xmlOutputBufferWrite(buf, 4, "&lt;");
            cur++;
            base = cur;
        } else if (*cur == '>') {
            if (base != cur)
                xmlOutputBufferWrite(buf, cur - base, (const char *) base);
            xmlOutputBufferWrite(buf, 4, "&gt;");
            cur++;
            base = cur;
        } else if (*cur == '&') {
            if (base != cur)
                xmlOutputBufferWrite(buf, cur - base, (const char *) base);
            xmlOutputBufferWrite(buf, 5, "&amp;");
            cur++;
            base = cur;
        } else if ((*cur >= 0x80) && (cur[1] != 0) &&
               ((doc == NULL) || (doc->encoding == NULL))) {
            /*
             * We assume we have UTF-8 content.
             */
            unsigned char tmp[12];
            int val = 0, l = 4;

            if (base != cur)
                xmlOutputBufferWrite(buf, cur - base, (const char *) base);

            val = xmlGetUTF8Char(cur, &l);
            if (val < 0) {
                val = 0xFFFD;
                cur++;
            } else {
                if (!IS_CHAR(val))
                    val = 0xFFFD;
                cur += l;
            }

            /*
             * We could do multiple things here. Just save
             * as a char ref
             */
        xmlSerializeHexCharRef(tmp, val);
            xmlOutputBufferWriteString(buf, (const char *) tmp);
            base = cur;
        } else {
            cur++;
        }
    }
    if (base != cur)
        xmlOutputBufferWrite(buf, cur - base, (const char *) base);
}

/**
 * xmlAttrSerializeTxtContent:
 * @buf:  the XML buffer output
 * @doc:  the document
 * @attr: the attribute node
 * @string: the text content
 *
 * Serialize text attribute values to an xml simple buffer
 */
void
xmlAttrSerializeTxtContent(xmlBufferPtr buf, xmlDocPtr doc,
                           xmlAttrPtr attr ATTRIBUTE_UNUSED,
                           const xmlChar *string)
{
    xmlOutputBufferPtr out;

    if ((buf == NULL) || (string == NULL))
        return;
    out = xmlOutputBufferCreateBuffer(buf, NULL);
    xmlBufAttrSerializeTxtContent(out, doc, string);
    xmlOutputBufferFlush(out);
    if ((out == NULL) || (out->error))
        xmlFree(xmlBufferDetach(buf));
    xmlOutputBufferClose(out);
}

/**
 * xmlNodeDump:
 * @buf:  the XML buffer output
 * @doc:  the document
 * @cur:  the current node
 * @level: the imbrication level for indenting
 * @format: is formatting allowed
 *
 * Dump an XML node, recursive behaviour,children are printed too.
 * Note that @format = 1 provide node indenting only if xmlIndentTreeOutput = 1
 * or xmlKeepBlanksDefault(0) was called.
 * Since this is using xmlBuffer structures it is limited to 2GB and somehow
 * deprecated, use xmlNodeDumpOutput() instead.
 *
 * Returns the number of bytes written to the buffer or -1 in case of error
 */
int
xmlNodeDump(xmlBufferPtr buf, xmlDocPtr doc, xmlNodePtr cur, int level,
            int format)
{
    xmlBufPtr buffer;
    size_t ret;

    if ((buf == NULL) || (cur == NULL))
        return(-1);
    if (level < 0)
        level = 0;
    else if (level > 100)
        level = 100;
    buffer = xmlBufFromBuffer(buf);
    if (buffer == NULL)
        return(-1);
    ret = xmlBufNodeDump(buffer, doc, cur, level, format);
    xmlBufBackToBuffer(buffer);
    if (ret > INT_MAX)
        return(-1);
    return(ret);
}

/**
 * xmlBufNodeDump:
 * @buf:  the XML buffer output
 * @doc:  the document
 * @cur:  the current node
 * @level: the imbrication level for indenting
 * @format: is formatting allowed
 *
 * Dump an XML node, recursive behaviour,children are printed too.
 * Note that @format = 1 provide node indenting only if xmlIndentTreeOutput = 1
 * or xmlKeepBlanksDefault(0) was called
 *
 * Returns the number of bytes written to the buffer, in case of error 0
 *     is returned or @buf stores the error
 */

size_t
xmlBufNodeDump(xmlBufPtr buf, xmlDocPtr doc, xmlNodePtr cur, int level,
            int format)
{
    size_t use;
    size_t ret;
    xmlOutputBufferPtr outbuf;
    int oldalloc;

    xmlInitParser();

    if (cur == NULL) {
        return ((size_t) -1);
    }
    if (buf == NULL) {
        return ((size_t) -1);
    }
    outbuf = (xmlOutputBufferPtr) xmlMalloc(sizeof(xmlOutputBuffer));
    if (outbuf == NULL) {
        xmlSaveErrMemory(NULL);
        return ((size_t) -1);
    }
    memset(outbuf, 0, (size_t) sizeof(xmlOutputBuffer));
    outbuf->buffer = buf;
    outbuf->encoder = NULL;
    outbuf->writecallback = NULL;
    outbuf->closecallback = NULL;
    outbuf->context = NULL;
    outbuf->written = 0;

    use = xmlBufUse(buf);
    oldalloc = xmlBufGetAllocationScheme(buf);
    xmlBufSetAllocationScheme(buf, XML_BUFFER_ALLOC_DOUBLEIT);
    xmlNodeDumpOutput(outbuf, doc, cur, level, format, NULL);
    xmlBufSetAllocationScheme(buf, oldalloc);
    if (outbuf->error)
        ret = (size_t) -1;
    else
        ret = xmlBufUse(buf) - use;
    xmlFree(outbuf);
    return (ret);
}

/**
 * xmlElemDump:
 * @f:  the FILE * for the output
 * @doc:  the document
 * @cur:  the current node
 *
 * Dump an XML/HTML node, recursive behaviour, children are printed too.
 */
void
xmlElemDump(FILE * f, xmlDocPtr doc, xmlNodePtr cur)
{
    xmlOutputBufferPtr outbuf;

    xmlInitParser();

    if (cur == NULL) {
        return;
    }

    outbuf = xmlOutputBufferCreateFile(f, NULL);
    if (outbuf == NULL)
        return;
#ifdef LIBXML_HTML_ENABLED
    if ((doc != NULL) && (doc->type == XML_HTML_DOCUMENT_NODE))
        htmlNodeDumpOutput(outbuf, doc, cur, NULL);
    else
#endif /* LIBXML_HTML_ENABLED */
        xmlNodeDumpOutput(outbuf, doc, cur, 0, 1, NULL);
    xmlOutputBufferClose(outbuf);
}

/************************************************************************
 *                                    *
 *        Saving functions front-ends                *
 *                                    *
 ************************************************************************/

/**
 * xmlNodeDumpOutput:
 * @buf:  the XML buffer output
 * @doc:  the document
 * @cur:  the current node
 * @level: the imbrication level for indenting
 * @format: is formatting allowed
 * @encoding:  an optional encoding string
 *
 * Dump an XML node, recursive behaviour, children are printed too.
 * Note that @format = 1 provide node indenting only if xmlIndentTreeOutput = 1
 * or xmlKeepBlanksDefault(0) was called
 */
void
xmlNodeDumpOutput(xmlOutputBufferPtr buf, xmlDocPtr doc, xmlNodePtr cur,
                  int level, int format, const char *encoding)
{
    xmlSaveCtxt ctxt;
#ifdef LIBXML_HTML_ENABLED
    xmlDtdPtr dtd;
    int is_xhtml = 0;
#endif

    (void) doc;

    xmlInitParser();

    if ((buf == NULL) || (cur == NULL)) return;

    if (level < 0)
        level = 0;
    else if (level > 100)
        level = 100;

    if (encoding == NULL)
        encoding = "UTF-8";

    memset(&ctxt, 0, sizeof(ctxt));
    ctxt.buf = buf;
    ctxt.level = level;
    ctxt.format = format ? 1 : 0;
    ctxt.encoding = (const xmlChar *) encoding;
    xmlSaveCtxtInit(&ctxt);
    ctxt.options |= XML_SAVE_AS_XML;

#ifdef LIBXML_HTML_ENABLED
    dtd = xmlGetIntSubset(doc);
    if (dtd != NULL) {
    is_xhtml = xmlIsXHTML(dtd->SystemID, dtd->ExternalID);
    if (is_xhtml < 0)
        is_xhtml = 0;
    }

    if (is_xhtml)
        xhtmlNodeDumpOutput(&ctxt, cur);
    else
#endif
        xmlNodeDumpOutputInternal(&ctxt, cur);
}

/**
 * xmlDocDumpFormatMemoryEnc:
 * @out_doc:  Document to generate XML text from
 * @doc_txt_ptr:  Memory pointer for allocated XML text
 * @doc_txt_len:  Length of the generated XML text
 * @txt_encoding:  Character encoding to use when generating XML text
 * @format:  should formatting spaces been added
 *
 * Dump the current DOM tree into memory using the character encoding specified
 * by the caller.  Note it is up to the caller of this function to free the
 * allocated memory with xmlFree().
 * Note that @format = 1 provide node indenting only if xmlIndentTreeOutput = 1
 * or xmlKeepBlanksDefault(0) was called
 */

void
xmlDocDumpFormatMemoryEnc(xmlDocPtr out_doc, xmlChar **doc_txt_ptr,
        int * doc_txt_len, const char * txt_encoding,
        int format) {
    xmlSaveCtxt ctxt;
    int                         dummy = 0;
    xmlOutputBufferPtr          out_buff = NULL;
    xmlCharEncodingHandlerPtr   conv_hdlr = NULL;

    if (doc_txt_len == NULL) {
        doc_txt_len = &dummy;   /*  Continue, caller just won't get length */
    }

    if (doc_txt_ptr == NULL) {
        *doc_txt_len = 0;
        return;
    }

    *doc_txt_ptr = NULL;
    *doc_txt_len = 0;

    if (out_doc == NULL) {
        /*  No document, no output  */
        return;
    }

    /*
     *  Validate the encoding value, if provided.
     *  This logic is copied from xmlSaveFileEnc.
     */

    if (txt_encoding == NULL)
    txt_encoding = (const char *) out_doc->encoding;
    if (txt_encoding != NULL) {
        int res;

    res = xmlOpenCharEncodingHandler(txt_encoding, /* output */ 1,
                                         &conv_hdlr);
    if (res != XML_ERR_OK) {
            xmlSaveErr(NULL, res, NULL, txt_encoding);
        return;
    }
    }

    if ((out_buff = xmlAllocOutputBuffer(conv_hdlr)) == NULL ) {
        xmlSaveErrMemory(NULL);
        xmlCharEncCloseFunc(conv_hdlr);
        return;
    }

    memset(&ctxt, 0, sizeof(ctxt));
    ctxt.buf = out_buff;
    ctxt.level = 0;
    ctxt.format = format ? 1 : 0;
    ctxt.encoding = (const xmlChar *) txt_encoding;
    xmlSaveCtxtInit(&ctxt);
    ctxt.options |= XML_SAVE_AS_XML;
    xmlDocContentDumpOutput(&ctxt, out_doc);
    xmlOutputBufferFlush(out_buff);

    if (!out_buff->error) {
        if (out_buff->conv != NULL) {
            *doc_txt_len = xmlBufUse(out_buff->conv);
            *doc_txt_ptr = xmlBufDetach(out_buff->conv);
        } else {
            *doc_txt_len = xmlBufUse(out_buff->buffer);
            *doc_txt_ptr = xmlBufDetach(out_buff->buffer);
        }
    }

    xmlOutputBufferClose(out_buff);
}

/**
 * xmlDocDumpMemory:
 * @cur:  the document
 * @mem:  OUT: the memory pointer
 * @size:  OUT: the memory length
 *
 * Dump an XML document in memory and return the #xmlChar * and it's size
 * in bytes. It's up to the caller to free the memory with xmlFree().
 * The resulting byte array is zero terminated, though the last 0 is not
 * included in the returned size.
 */
void
xmlDocDumpMemory(xmlDocPtr cur, xmlChar**mem, int *size) {
    xmlDocDumpFormatMemoryEnc(cur, mem, size, NULL, 0);
}

/**
 * xmlDocDumpFormatMemory:
 * @cur:  the document
 * @mem:  OUT: the memory pointer
 * @size:  OUT: the memory length
 * @format:  should formatting spaces been added
 *
 *
 * Dump an XML document in memory and return the #xmlChar * and it's size.
 * It's up to the caller to free the memory with xmlFree().
 * Note that @format = 1 provide node indenting only if xmlIndentTreeOutput = 1
 * or xmlKeepBlanksDefault(0) was called
 */
void
xmlDocDumpFormatMemory(xmlDocPtr cur, xmlChar**mem, int *size, int format) {
    xmlDocDumpFormatMemoryEnc(cur, mem, size, NULL, format);
}

/**
 * xmlDocDumpMemoryEnc:
 * @out_doc:  Document to generate XML text from
 * @doc_txt_ptr:  Memory pointer for allocated XML text
 * @doc_txt_len:  Length of the generated XML text
 * @txt_encoding:  Character encoding to use when generating XML text
 *
 * Dump the current DOM tree into memory using the character encoding specified
 * by the caller.  Note it is up to the caller of this function to free the
 * allocated memory with xmlFree().
 */

void
xmlDocDumpMemoryEnc(xmlDocPtr out_doc, xmlChar **doc_txt_ptr,
                int * doc_txt_len, const char * txt_encoding) {
    xmlDocDumpFormatMemoryEnc(out_doc, doc_txt_ptr, doc_txt_len,
                          txt_encoding, 0);
}

/**
 * xmlDocFormatDump:
 * @f:  the FILE*
 * @cur:  the document
 * @format: should formatting spaces been added
 *
 * Dump an XML document to an open FILE.
 *
 * returns: the number of bytes written or -1 in case of failure.
 * Note that @format = 1 provide node indenting only if xmlIndentTreeOutput = 1
 * or xmlKeepBlanksDefault(0) was called
 */
int
xmlDocFormatDump(FILE *f, xmlDocPtr cur, int format) {
    xmlSaveCtxt ctxt;
    xmlOutputBufferPtr buf;
    const char * encoding;
    xmlCharEncodingHandlerPtr handler = NULL;
    int ret;

    if (cur == NULL) {
    return(-1);
    }
    encoding = (const char *) cur->encoding;

    if (encoding != NULL) {
        int res;

    res = xmlOpenCharEncodingHandler(encoding, /* output */ 1, &handler);
    if (res != XML_ERR_OK) {
        xmlFree((char *) cur->encoding);
        cur->encoding = NULL;
        encoding = NULL;
    }
    }
    buf = xmlOutputBufferCreateFile(f, handler);
    if (buf == NULL) return(-1);
    memset(&ctxt, 0, sizeof(ctxt));
    ctxt.buf = buf;
    ctxt.level = 0;
    ctxt.format = format ? 1 : 0;
    ctxt.encoding = (const xmlChar *) encoding;
    xmlSaveCtxtInit(&ctxt);
    ctxt.options |= XML_SAVE_AS_XML;
    xmlDocContentDumpOutput(&ctxt, cur);

    ret = xmlOutputBufferClose(buf);
    return(ret);
}

/**
 * xmlDocDump:
 * @f:  the FILE*
 * @cur:  the document
 *
 * Dump an XML document to an open FILE.
 *
 * returns: the number of bytes written or -1 in case of failure.
 */
int
xmlDocDump(FILE *f, xmlDocPtr cur) {
    return(xmlDocFormatDump (f, cur, 0));
}

/**
 * xmlSaveFileTo:
 * @buf:  an output I/O buffer
 * @cur:  the document
 * @encoding:  the encoding if any assuming the I/O layer handles the transcoding
 *
 * Dump an XML document to an I/O buffer.
 * Warning ! This call xmlOutputBufferClose() on buf which is not available
 * after this call.
 *
 * returns: the number of bytes written or -1 in case of failure.
 */
int
xmlSaveFileTo(xmlOutputBufferPtr buf, xmlDocPtr cur, const char *encoding) {
    xmlSaveCtxt ctxt;
    int ret;

    if (buf == NULL) return(-1);
    if (cur == NULL) {
        xmlOutputBufferClose(buf);
    return(-1);
    }
    memset(&ctxt, 0, sizeof(ctxt));
    ctxt.buf = buf;
    ctxt.level = 0;
    ctxt.format = 0;
    ctxt.encoding = (const xmlChar *) encoding;
    xmlSaveCtxtInit(&ctxt);
    ctxt.options |= XML_SAVE_AS_XML;
    xmlDocContentDumpOutput(&ctxt, cur);
    ret = xmlOutputBufferClose(buf);
    return(ret);
}

/**
 * xmlSaveFormatFileTo:
 * @buf:  an output I/O buffer
 * @cur:  the document
 * @encoding:  the encoding if any assuming the I/O layer handles the transcoding
 * @format: should formatting spaces been added
 *
 * Dump an XML document to an I/O buffer.
 * Warning ! This call xmlOutputBufferClose() on buf which is not available
 * after this call.
 *
 * returns: the number of bytes written or -1 in case of failure.
 */
int
xmlSaveFormatFileTo(xmlOutputBufferPtr buf, xmlDocPtr cur,
                    const char *encoding, int format)
{
    xmlSaveCtxt ctxt;
    int ret;

    if (buf == NULL) return(-1);
    if ((cur == NULL) ||
        ((cur->type != XML_DOCUMENT_NODE) &&
     (cur->type != XML_HTML_DOCUMENT_NODE))) {
        xmlOutputBufferClose(buf);
    return(-1);
    }
    memset(&ctxt, 0, sizeof(ctxt));
    ctxt.buf = buf;
    ctxt.level = 0;
    ctxt.format = format ? 1 : 0;
    ctxt.encoding = (const xmlChar *) encoding;
    xmlSaveCtxtInit(&ctxt);
    ctxt.options |= XML_SAVE_AS_XML;
    xmlDocContentDumpOutput(&ctxt, cur);
    ret = xmlOutputBufferClose(buf);
    return (ret);
}

/**
 * xmlSaveFormatFileEnc:
 * @filename:  the filename or URL to output
 * @cur:  the document being saved
 * @encoding:  the name of the encoding to use or NULL.
 * @format:  should formatting spaces be added.
 *
 * Dump an XML document to a file or an URL.
 *
 * Returns the number of bytes written or -1 in case of error.
 * Note that @format = 1 provide node indenting only if xmlIndentTreeOutput = 1
 * or xmlKeepBlanksDefault(0) was called
 */
int
xmlSaveFormatFileEnc( const char * filename, xmlDocPtr cur,
            const char * encoding, int format ) {
    xmlSaveCtxt ctxt;
    xmlOutputBufferPtr buf;
    xmlCharEncodingHandlerPtr handler = NULL;
    int ret;

    if (cur == NULL)
    return(-1);

    if (encoding == NULL)
    encoding = (const char *) cur->encoding;

    if (encoding != NULL) {
        int res;

        res = xmlOpenCharEncodingHandler(encoding, /* output */ 1, &handler);
        if (res != XML_ERR_OK)
            return(-1);
    }

#ifdef LIBXML_ZLIB_ENABLED
    if (cur->compression < 0) cur->compression = xmlGetCompressMode();
#endif
    /*
     * save the content to a temp buffer.
     */
    buf = xmlOutputBufferCreateFilename(filename, handler, cur->compression);
    if (buf == NULL) return(-1);
    memset(&ctxt, 0, sizeof(ctxt));
    ctxt.buf = buf;
    ctxt.level = 0;
    ctxt.format = format ? 1 : 0;
    ctxt.encoding = (const xmlChar *) encoding;
    xmlSaveCtxtInit(&ctxt);
    ctxt.options |= XML_SAVE_AS_XML;

    xmlDocContentDumpOutput(&ctxt, cur);

    ret = xmlOutputBufferClose(buf);
    return(ret);
}


/**
 * xmlSaveFileEnc:
 * @filename:  the filename (or URL)
 * @cur:  the document
 * @encoding:  the name of an encoding (or NULL)
 *
 * Dump an XML document, converting it to the given encoding
 *
 * returns: the number of bytes written or -1 in case of failure.
 */
int
xmlSaveFileEnc(const char *filename, xmlDocPtr cur, const char *encoding) {
    return ( xmlSaveFormatFileEnc( filename, cur, encoding, 0 ) );
}

/**
 * xmlSaveFormatFile:
 * @filename:  the filename (or URL)
 * @cur:  the document
 * @format:  should formatting spaces been added
 *
 * Dump an XML document to a file. Will use compression if
 * compiled in and enabled. If @filename is "-" the stdout file is
 * used. If @format is set then the document will be indented on output.
 * Note that @format = 1 provide node indenting only if xmlIndentTreeOutput = 1
 * or xmlKeepBlanksDefault(0) was called
 *
 * returns: the number of bytes written or -1 in case of failure.
 */
int
xmlSaveFormatFile(const char *filename, xmlDocPtr cur, int format) {
    return ( xmlSaveFormatFileEnc( filename, cur, NULL, format ) );
}

/**
 * xmlSaveFile:
 * @filename:  the filename (or URL)
 * @cur:  the document
 *
 * Dump an XML document to a file. Will use compression if
 * compiled in and enabled. If @filename is "-" the stdout file is
 * used.
 * returns: the number of bytes written or -1 in case of failure.
 */
int
xmlSaveFile(const char *filename, xmlDocPtr cur) {
    return(xmlSaveFormatFileEnc(filename, cur, NULL, 0));
}

#endif /* LIBXML_OUTPUT_ENABLED */

