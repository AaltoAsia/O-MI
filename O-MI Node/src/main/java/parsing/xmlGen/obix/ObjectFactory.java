//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.03.20 at 02:03:34 PM EET 
//


package parsing.xmlGen.obix;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the org.obix.ns.schema._1 package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _Abstime_QNAME = new QName("http://obix.org/ns/schema/1.0", "abstime");
    private final static QName _Obj_QNAME = new QName("http://obix.org/ns/schema/1.0", "obj");
    private final static QName _Ref_QNAME = new QName("http://obix.org/ns/schema/1.0", "ref");
    private final static QName _Feed_QNAME = new QName("http://obix.org/ns/schema/1.0", "feed");
    private final static QName _Str_QNAME = new QName("http://obix.org/ns/schema/1.0", "str");
    private final static QName _Enum_QNAME = new QName("http://obix.org/ns/schema/1.0", "enum");
    private final static QName _Int_QNAME = new QName("http://obix.org/ns/schema/1.0", "int");
    private final static QName _Reltime_QNAME = new QName("http://obix.org/ns/schema/1.0", "reltime");
    private final static QName _Uri_QNAME = new QName("http://obix.org/ns/schema/1.0", "uri");
    private final static QName _List_QNAME = new QName("http://obix.org/ns/schema/1.0", "list");
    private final static QName _Real_QNAME = new QName("http://obix.org/ns/schema/1.0", "real");
    private final static QName _Bool_QNAME = new QName("http://obix.org/ns/schema/1.0", "bool");
    private final static QName _Err_QNAME = new QName("http://obix.org/ns/schema/1.0", "err");
    private final static QName _Op_QNAME = new QName("http://obix.org/ns/schema/1.0", "op");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: org.obix.ns.schema._1
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Op }
     * 
     */
    public Op createOp() {
        return new Op();
    }

    /**
     * Create an instance of {@link Obj }
     * 
     */
    public Obj createObj() {
        return new Obj();
    }

    /**
     * Create an instance of {@link Bool }
     * 
     */
    public Bool createBool() {
        return new Bool();
    }

    /**
     * Create an instance of {@link Real }
     * 
     */
    public Real createReal() {
        return new Real();
    }

    /**
     * Create an instance of {@link List }
     * 
     */
    public List createList() {
        return new List();
    }

    /**
     * Create an instance of {@link Uri }
     * 
     */
    public Uri createUri() {
        return new Uri();
    }

    /**
     * Create an instance of {@link Enum }
     * 
     */
    public Enum createEnum() {
        return new Enum();
    }

    /**
     * Create an instance of {@link Int }
     * 
     */
    public Int createInt() {
        return new Int();
    }

    /**
     * Create an instance of {@link RelTime }
     * 
     */
    public RelTime createRelTime() {
        return new RelTime();
    }

    /**
     * Create an instance of {@link Str }
     * 
     */
    public Str createStr() {
        return new Str();
    }

    /**
     * Create an instance of {@link Feed }
     * 
     */
    public Feed createFeed() {
        return new Feed();
    }

    /**
     * Create an instance of {@link AbsTime }
     * 
     */
    public AbsTime createAbsTime() {
        return new AbsTime();
    }

    /**
     * Create an instance of {@link Err }
     * 
     */
    public Err createErr() {
        return new Err();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AbsTime }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "abstime", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<AbsTime> createAbstime(AbsTime value) {
        return new JAXBElement<AbsTime>(_Abstime_QNAME, AbsTime.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Obj }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "obj")
    public JAXBElement<Obj> createObj(Obj value) {
        return new JAXBElement<Obj>(_Obj_QNAME, Obj.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Obj }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "ref", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Obj> createRef(Obj value) {
        return new JAXBElement<Obj>(_Ref_QNAME, Obj.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Feed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "feed", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Feed> createFeed(Feed value) {
        return new JAXBElement<Feed>(_Feed_QNAME, Feed.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Str }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "str", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Str> createStr(Str value) {
        return new JAXBElement<Str>(_Str_QNAME, Str.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Enum }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "enum", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Enum> createEnum(Enum value) {
        return new JAXBElement<Enum>(_Enum_QNAME, Enum.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Int }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "int", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Int> createInt(Int value) {
        return new JAXBElement<Int>(_Int_QNAME, Int.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RelTime }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "reltime", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<RelTime> createReltime(RelTime value) {
        return new JAXBElement<RelTime>(_Reltime_QNAME, RelTime.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Uri }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "uri", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Uri> createUri(Uri value) {
        return new JAXBElement<Uri>(_Uri_QNAME, Uri.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link List }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "list", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<List> createList(List value) {
        return new JAXBElement<List>(_List_QNAME, List.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Real }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "real", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Real> createReal(Real value) {
        return new JAXBElement<Real>(_Real_QNAME, Real.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Bool }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "bool", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Bool> createBool(Bool value) {
        return new JAXBElement<Bool>(_Bool_QNAME, Bool.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Obj }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "err", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Obj> createErr(Obj value) {
        return new JAXBElement<Obj>(_Err_QNAME, Obj.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Op }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://obix.org/ns/schema/1.0", name = "op", substitutionHeadNamespace = "http://obix.org/ns/schema/1.0", substitutionHeadName = "obj")
    public JAXBElement<Op> createOp(Op value) {
        return new JAXBElement<Op>(_Op_QNAME, Op.class, null, value);
    }

}
