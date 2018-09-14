package rest

import fiware.Ngsi2LdModelMapper
import org.apache.http.HttpEntity
import org.apache.http.util.EntityUtils
import org.scalatra.Params
import utils.{LdContextResolver, ParserUtil}

import scala.collection.mutable

/**
  *
  * Utilities for the NGSI-LD Wrapper
  *
  * Coypright (c) 2018 FIWARE Foundation e.V.
  *
  * Author: José M. Cantera
  *
  * LICENSE: MIT
  *
  *
  */
trait WrapperUtils {
  def KeyValues = "keyValues"

  def DefaultContextLink =
    """<https://fiware.github.io/NGSI-LD_Tests/ldContext/defaultContext.jsonld>;
                             rel="http://www.w3.org/ns/json-ld#context";
                             type="application/ld+json";"""


  def toNgsiLd(params: Params, in: Map[String, Any], ldContext: Map[String, String]) = {
    if (mode(params) == KeyValues) Ngsi2LdModelMapper.fromNgsiKeyValues(in, ldContext)
    else Ngsi2LdModelMapper.fromNgsi(in, ldContext)
  }

  def mode(params: Params) = {
    var options = params.getOrElse("options", null)
    if (options != null)
      if (options.split(",").indexOf(KeyValues) != -1)
        options = KeyValues

    options
  }

  def errorDescription(httpEntity: HttpEntity) = {
    val errorPayload = ParserUtil.parse(EntityUtils.toString(
      httpEntity, "UTF-8")).asInstanceOf[Map[String, String]]

    Some(errorPayload("description"))
  }

  def defaultContext = Map("Link" -> DefaultContextLink)

  def parseAccept(accept: String): Array[String] = {
    val mimeTypes = accept.split(',')

    mimeTypes.map((t) => t.split(';')(0))
  }

  /* Obtains the LD Context of an Entity encoded in NGSI-LD */
  def ldContext(data: Map[String, Any]): Map[String, String] = {
    val ldContext = data.getOrElse("@context", Map[String, String]())

    resolveContext(ldContext)
  }

  def resolveContext(ldContextValue: Any): Map[String, String] = {
    val ldContextResolved = mutable.Map[String, String]()

    if (ldContextValue.isInstanceOf[String]) {
      LdContextResolver.resolveContext(ldContextValue.asInstanceOf[String], ldContextResolved)
    }
    else if (ldContextValue.isInstanceOf[List[Any]]) {
      val ldContextLocs = ldContextValue.asInstanceOf[List[Any]]
      ldContextLocs.foreach(l => {
        if (l.isInstanceOf[String]) {
          LdContextResolver.resolveContext(l.asInstanceOf[String], ldContextResolved)
        }
        else if (l.isInstanceOf[Map[String, String]]) {
          ldContextResolved ++= l.asInstanceOf[Map[String, String]]
        }
      })
    }
    else if (ldContextValue.isInstanceOf[Map[String, String]]) {
      ldContextResolved ++= ldContextValue.asInstanceOf[Map[String, String]]
    }

    ldContextResolved.toMap[String, String]
  }

  def partialAttrCheck(payloadData: Map[String, Any], ngsiData: Any, attribute: String): ValidationResult = {
    if (payloadData.isEmpty)
      EmptyPayload()
    else {
      // If the Entity does not exist or currently does not have such an Attribute 404 is returned
      if (ngsiData == None)
        EntityNotFound()
      else {
        val entityData = ngsiData.asInstanceOf[Map[String, Any]]
        // TODO: This might need to be checked taking into account a @context
        if (!entityData.contains(attribute))
          AttributeNotFound()
        else
          ValidInput()
      }
    }
  }

  def rewriteQueryString(params: Params, in: String): String = {
    var out = in

    val attrs = params.getOrElse("attrs", None)
    if (attrs != None) {
      // Hack to allow getting the @context
      out += s"&attrs=${attrs},@context"
    }

    var geoRel = params.getOrElse("georel", None)
    if (geoRel != None) {
      geoRel = geoRel.asInstanceOf[String].replace("==",":")
    }

    val coords = params.getOrElse("coordinates", None)
    val geometry = params.getOrElse("geometry", None)

    if (coords != None && geometry != None) {
      out += s"&geometry=point&georel=${geoRel}"

      // Transformation to GeoJson object
      val parsedCoordinates = ParserUtil.parse(coords.asInstanceOf[String])

      // For the moment only GeoJSON objects of type "Point" are supported
      val coordsArray = parsedCoordinates.asInstanceOf[List[Any]]
      geometry match {
        case "Point" => out += s"&coords=${coordsArray(1)},${coordsArray(0)}"
        // TODO: Support other GeoJSON geometries
        case _ => {  }
      }
    }

    out
  }
}