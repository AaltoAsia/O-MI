/**********************************************************************************
 *    Copyright (c) 2015 Aalto University.                                        *
 *                                                                                *
 *    Licensed under the 4-clause BSD (the "License");                            *
 *    you may not use this file except in compliance with the License.            *
 *    You may obtain a copy of the License at top most directory of project.      *
 *                                                                                *
 *    Unless required by applicable law or agreed to in writing, software         *
 *    distributed under the License is distributed on an "AS IS" BASIS,           *
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    *
 *    See the License for the specific language governing permissions and         *
 *    limitations under the License.                                              *
 **********************************************************************************/

package accessControl;

import akka.http.scaladsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.headers.HttpCookie;
import akka.http.scaladsl.model.headers.HttpCookiePair;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

import http.*;
import jdk.nashorn.internal.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import parsing.xmlGen.odf.*;
import parsing.xmlGen.omi.MsgType;
import parsing.xmlGen.omi.ObjectFactory;
import parsing.xmlGen.omi.OmiEnvelopeType;
import parsing.xmlGen.omi.WriteRequestType;
import types.OmiTypes.OmiRequest;
import types.Path;
import types.OmiTypes.UserInfo;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import java.util.Iterator;


public class AuthAPIService implements AuthApi {

    //TODO Settable
    private final boolean useHTTPS = false;
    private final int authServicePort = 80;
    private final String authServiceURIScheme = useHTTPS ? "https://" : "http://";
    private final String mainURI = useHTTPS ? "localhost" : "localhost:"+authServicePort;
    private final String authServiceURI = authServiceURIScheme + mainURI + "/omi/auth0/permissions";

    private final Logger logger = LoggerFactory.getLogger(AuthAPIService.class);


    static {
        //for localhost testing only
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
                new javax.net.ssl.HostnameVerifier(){

                    public boolean verify(String hostname,  javax.net.ssl.SSLSession sslSession) {
                        return hostname.equals("localhost");
                    }
                });
    }


    @Override
    public AuthorizationResult isAuthorizedForType(HttpRequest httpRequest,
                                boolean isWrite,
                                java.lang.Iterable<Path> paths) {

        logger.debug("isAuthorizedForType EXECUTED!");


        String subjectInfo = null;
        boolean success = false;
        boolean authenticated = false;

        // First try authenticate user by cookies
        scala.collection.Iterator<HttpCookiePair> iter = httpRequest.cookies().iterator();
        if (!iter.hasNext()) {
            logger.debug("No cookies!");

        } else {

            HttpCookie ck = null;
            while (iter.hasNext()) {
                HttpCookie nextCookie = iter.next().toCookie();
                logger.debug(nextCookie.name() + ":" + nextCookie.value());

                if (nextCookie.name().equals("JSESSIONID")) {
                    ck = nextCookie;
                    break;
                }
            }

            if (ck != null)
            {
                authenticated = true;
                subjectInfo = ck.toString();
            }

        }


        // If there is not certificate present we try to find the certificate
        if (!authenticated) {

            scala.collection.Iterator<HttpHeader> iterh = httpRequest.headers().iterator();

            while (iterh.hasNext()) {

                HttpHeader nextHeader = (HttpHeader)iterh.next();
                if (nextHeader.name().equals("X-SSL-CLIENT")) {
                    String allInfo = nextHeader.value();
                    subjectInfo = allInfo.substring(allInfo.indexOf("emailAddress=") + "emailAddress=".length());

                    if (success)
                        break;

                } else if (nextHeader.name().equals("X-SSL-VERIFY")) {
                    success = nextHeader.value().contains("SUCCESS");

                    if (subjectInfo != null)
                        break;
                }
            }

            authenticated = (subjectInfo != null) && success;
            if (authenticated)
            {
                logger.debug("Received user certificate, data:\nemailAddress="+subjectInfo+"\nvalidated="+success);
            }
        }


        // Start parsing request
        if (authenticated) {

            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                String nextObj = iterator.next().toString();

                // the very first query to read the tree
                if (nextObj.equalsIgnoreCase("Objects")) {
                    logger.debug("Root tree requested. forwarding to Partial API.");

                    //Getting paths according to the policies
                    ArrayList<Path> res_paths = getAvailablePaths(subjectInfo, success); //////////////////////////////

                    if (res_paths == null)
                        return new Unauthorized(new UserInfo(UserInfo.apply$default$1(), UserInfo.apply$default$2())); // UserInfo

                    // Check if security module return "all" means allowing all tree (administrator mode or read_all mode)
                    if (res_paths.size() == 1) {
                        String obj_path = res_paths.get(0).toString();
                        if (obj_path.equalsIgnoreCase("all"))
                            return new Authorized(new UserInfo(UserInfo.apply$default$1(), UserInfo.apply$default$2()));
                    }

                    return new Partial(res_paths, new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()));
                } else
                    break;
            }

            String requestBody = "{\"paths\":[";
            Iterator<Path> it = paths.iterator();
            while (it.hasNext()) {
                String nextObj = it.next().toString();

                // the very first query to read the tree
                if (nextObj.equalsIgnoreCase("Objects"))
                    return new Authorized(new UserInfo(UserInfo.apply$default$1(), UserInfo.apply$default$2()));

                requestBody += "\"" + nextObj + "\"";

                if (it.hasNext())
                    requestBody += ",";
            }

            if (success)
                requestBody += "],\"user\":\"" + subjectInfo + "\"}";
            else
                requestBody += "]}";

            logger.debug("isWrite:" + isWrite);
            logger.debug("Paths:" + requestBody);

            return sendPermissionRequest(isWrite, requestBody, subjectInfo, success); ////////////////////////////////
        } else {
            return new Unauthorized(new UserInfo(UserInfo.apply$default$1(), UserInfo.apply$default$2()));
        }
    }

    private void createOMIRequest(String escapedData, String userInfo) {
        ObjectFactory omiObjFactory = new ObjectFactory();
        parsing.xmlGen.odf.ObjectFactory odfObjFactory = new parsing.xmlGen.odf.ObjectFactory();

        OmiEnvelopeType envelope = omiObjFactory.createOmiEnvelopeType();
        envelope.setTtl("0");
        envelope.setVersion("1.0");

        WriteRequestType writeReq = omiObjFactory.createWriteRequestType();
        writeReq.setMsgformat("odf");

        ObjectsType allObjects = odfObjFactory.createObjectsType();

        ObjectType authObj = odfObjFactory.createObjectType();
        QlmIDType authID = odfObjFactory.createQlmIDType();
        authID.setValue("AuthorizationRequest");


        // Auth info item
        InfoItemType authInfoItem = odfObjFactory.createInfoItemType();
        authInfoItem.setNameAttribute("OriginalRequest");

        ValueType originalReq = odfObjFactory.createValueType();
        originalReq.setType("omi.xsd");
        originalReq.setValue(escapedData);

        // User info item
        InfoItemType userInfoItem = odfObjFactory.createInfoItemType();
        userInfoItem.setNameAttribute("UserInfo");

        ValueType userInfoVal = odfObjFactory.createValueType();
        userInfoVal.setType("xs:string");
        userInfoVal.setValue(userInfo);

        // Setters
        // Two infoitems with values
        userInfoItem.getValue().add(userInfoVal);
        authInfoItem.getValue().add(originalReq);

        // Add object
        authObj.getId().add(authID);
        authObj.getInfoItem().add(authInfoItem);
        authObj.getInfoItem().add(userInfoItem);

        allObjects.getObject().add(authObj);

        MsgType msg = new MsgType();
        msg.getContent().addAll(allObjects.getObject());

        // Write request
        writeReq.setMsg(msg);

        // Envelope
        envelope.setWrite(writeReq);

        try {

            JAXBContext jaxbContext = JAXBContext.newInstance("parsing.xmlGen.omi:parsing.xmlGen.odf");
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,
                    Boolean.TRUE);
            marshaller.marshal(envelope,
                    new FileOutputStream("jaxbOutput.xml"));
        } catch (Exception ex) {
            logger.error("Error:",ex);
        }
    }

    //// when disabled, use the default
    //public AuthorizationResult isAuthorizedForType(HttpRequest httpRequest,
    //                            boolean isWrite,
    //                            java.lang.Iterable<Path> paths) {
    //    return  AuthApi$class.isAuthorizedForType(this, httpRequest, isWrite, paths);
    //}

    public AuthorizationResult isAuthorizedForRequest(HttpRequest httpRequest,
                                   OmiRequest omiRequest) {
        return AuthApi$class.isAuthorizedForRequest(this, httpRequest, omiRequest);
    }

    public AuthorizationResult isAuthorizedForRawRequest(HttpRequest httpRequest,
                                   String request) {
        logger.debug("isAuthorizedForRawRequest EXECUTED!");
        //System.out.println("isAuthorizedForRawRequest EXECUTED!");
        return AuthApi$class.isAuthorizedForRawRequest(this, httpRequest, request);
    }

//    // TODO: FIXME: needs xml escaping and creating the O-MI request to transfer it
//    public AuthorizationResult isAuthorizedForRawRequest(HttpRequest httpRequest,
//                                   String request) {
//        logger.debug("isAuthorizedForRawRequest EXECUTED!");
//        //System.out.println("isAuthorizedForRawRequest EXECUTED!");
///        return AuthApi$class.isAuthorizedForRawRequest(this, httpRequest, request);
//
//        String subjectInfo = null;
//        boolean success = false;
//        boolean authenticated = false;
//
//        // First try authenticate user by cookies
//        scala.collection.Iterator  iter = httpRequest.cookies().iterator();
//        if (!iter.hasNext()) {
//            logger.debug("No cookies!");
//
//        } else {
//
//            HttpCookie ck = null;
//            while (iter.hasNext()) {
//                HttpCookie nextCookie = (HttpCookie) iter.next();
//                logger.debug(nextCookie.name() + ":" + nextCookie.value());
//
//                if (nextCookie.name().equals("JSESSIONID")) {
//                    ck = nextCookie;
//                    break;
//                }
//            }
//
//            if (ck != null)
//            {
//                authenticated = true;
//                subjectInfo = ck.toString();
//            }
//
//        }
//
//        // If there is not certificate present we try to find the certificate
//        if (!authenticated) {
//
//            iter = httpRequest.headers().iterator();
//
//            while (iter.hasNext()) {
//
//                HttpHeader nextHeader = (HttpHeader)iter.next();
//                if (nextHeader.name().equals("X-SSL-CLIENT")) {
//                    String allInfo = nextHeader.value();
//                    subjectInfo = allInfo.substring(allInfo.indexOf("emailAddress=") + "emailAddress=".length());
//
//                    if (success)
//                        break;
//
//                } else if (nextHeader.name().equals("X-SSL-VERIFY")) {
//                    success = nextHeader.value().contains("SUCCESS");
//
//                    if (subjectInfo != null)
//                        break;
//                }
//            }
//
//            authenticated = (subjectInfo != null) && success;
//            if (authenticated)
//            {
//                logger.debug("Received user certificate, data:\nemailAddress="+subjectInfo+"\nvalidated="+success);
//            }
//        }
//
//        if (authenticated) {
//            createOMIRequest(request,subjectInfo);
//            return Authorized.instance();
//        }
//        return Unauthorized.instance();
//    }

    public ArrayList<Path> getAvailablePaths(String subjectInfo, boolean isCertificate) {

        HttpURLConnection connection = null;
        try {
            //Create connection
            String finalURL = authServiceURI + "?getPaths=true";
            logger.debug("Sending request. URI:" + finalURL);
            URL url = new URL(finalURL);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/json");

            if (!isCertificate)
                connection.setRequestProperty("Cookie", subjectInfo);

            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.connect();


            if (isCertificate) {
                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(subjectInfo);
                wr.flush();
                wr.close();
            }

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
            String line;
            while((line = rd.readLine()) != null) {
                response.append(line);
            }
            rd.close();

            String response_result = response.toString();

            // If the whole tree is allowed (administrator mode)
            // We need this since the list of administrators is stored on AC side
            if (response_result.equalsIgnoreCase("true"))
            {
                ArrayList<Path> res = new ArrayList<>();
                res.add(new Path("all"));
                return res;
            } else if (response_result.equalsIgnoreCase("false"))
                return null;


            // Parse the paths and return them
            JsonObject paths = new JsonParser().parse(response_result).getAsJsonObject();
            JsonArray json_paths = paths.getAsJsonArray("paths");

            ArrayList<Path> finalPaths = new ArrayList<>(json_paths.size());

            logger.debug(json_paths.size()+" PATHS FOUND");

            for (int i = 0; i < json_paths.size(); i++) {
                String pathString = json_paths.get(i).getAsString();
                logger.debug(pathString);
                Path nextPath = new Path(pathString);
                finalPaths.add(nextPath);
            }

            return finalPaths;

        } catch (Exception e) {
            logger.error("During getAvailablePaths request", e);
            return null;
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }

    public AuthorizationResult sendPermissionRequest(boolean isWrite, String body, String subjectInfo, boolean isCertificate) {
        HttpURLConnection connection = null;
        try {
            //Create connection
            String writeURL = isWrite ? "true" : "false";
            String finalURL = authServiceURI + "?ac=true&write=" + writeURL;
            logger.debug("Sending request. URI:" + finalURL);
            URL url = new URL(finalURL);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/json");

            if (!isCertificate)
                connection.setRequestProperty("Cookie", subjectInfo);

            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.connect();

            //Send request
            DataOutputStream wr = new DataOutputStream (
                    connection.getOutputStream());
            wr.writeBytes(body);
            wr.close();

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
            String line;
            while((line = rd.readLine()) != null) {
                response.append(line);
            }
            rd.close();

            logger.debug("RESPONSE:"+response.toString());

            if (response.toString().equals("false")) {
                return new Unauthorized(new UserInfo(UserInfo.apply$default$1(), UserInfo.apply$default$2()));
            }

            JsonObject responseObject = new JsonParser().parse(response.toString()).getAsJsonObject();//response.toString(); //reuse variable
            String isAuthenticated = responseObject.get("result").getAsString();
            String userName = responseObject.get("userID").getAsString();
            return isAuthenticated.equalsIgnoreCase("ok") ?
                    new Authorized(
                            new UserInfo(
                                    UserInfo.apply$default$1(),
                                    scala.Option.apply(userName)))
                    :
                    new Unauthorized(
                            new UserInfo(
                                    UserInfo.apply$default$1(),
                                    scala.Option.apply(userName)));
        } catch (Exception e) {
            logger.error("During http request", e);
            return new Unauthorized(new UserInfo(UserInfo.apply$default$1(), UserInfo.apply$default$2()));
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }

//    public AuthorizationResult sendPermissionRequest(boolean isWrite, String body, String subjectInfo, boolean isCertificate) {
//        HttpURLConnection connection = null;
//        try {
//            //Create connection
//            String writeURL = isWrite ? "true" : "false";
//            String finalURL = authServiceURI + "?ac=true&write=" + writeURL;
//            logger.debug("Sending request. URI:" + finalURL);
//            URL url = new URL(finalURL);
//            connection = (HttpURLConnection)url.openConnection();
//            connection.setRequestMethod("POST");
//            connection.setRequestProperty("Content-Type",
//                    "application/json");
//
//            if (!isCertificate)
//                connection.setRequestProperty("Cookie", subjectInfo);
//
//            connection.setUseCaches(false);
//            connection.setDoOutput(true);
//            connection.connect();
//
//            //Send request
//            DataOutputStream wr = new DataOutputStream (
//                    connection.getOutputStream());
//            wr.writeBytes(body);
//            wr.close();
//
//            //Get Response
//            InputStream is = connection.getInputStream();
//            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
//            StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
//            String line;
//            while((line = rd.readLine()) != null) {
//                response.append(line);
//            }
//            rd.close();
//
//            logger.debug("RESPONSE:"+response.toString());
//
//            return response.toString().equalsIgnoreCase("true") ? Authorized.instance() : Unauthorized.instance();
//        } catch (Exception e) {
//            logger.error("During http request", e);
//            return Unauthorized.instance();
//        } finally {
//            if(connection != null) {
//                connection.disconnect();
//            }
//        }
//    }
}
