package accessControl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import http.AuthApi;
import http.AuthApi$class;
import http.AuthorizationResult;
import http.Authorized;
import http.Unauthorized;
import http.Partial;
import http.AuthorizationResult;
import scala.collection.immutable.List;
import spray.http.HttpCookie;
import spray.http.HttpHeader;
import types.Path;
import types.OmiTypes.OmiRequest;
import scala.collection.JavaConverters.*;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by romanfilippov on 14/01/16.
 */
public class AuthAPIService implements AuthApi {

    private final boolean useHTTPS = false;
    private final int authServicePort = 8088;
    private final String authServiceURIScheme = useHTTPS ? "https://" : "http://";
    private final String mainURI = useHTTPS ? "localhost" : "localhost:"+authServicePort;
    private final String authServiceURI = authServiceURIScheme + mainURI + "/security/PermissionService";

    private final Logger logger = LoggerFactory.getLogger(AuthAPIService.class);


    static {
        //for localhost testing only
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(
                new javax.net.ssl.HostnameVerifier(){

                    public boolean verify(String hostname,
                                          javax.net.ssl.SSLSession sslSession) {
                        if (hostname.equals("localhost")) {
                            return true;
                        }
                        return false;
                    }
                });
    }

//    @Override
//    public boolean isAuthorizedForType(spray.http.HttpRequest httpRequest,
//                                boolean isWrite,
//                                java.lang.Iterable<Path> paths) {
//
//        logger.info("isAuthorizedForType EXECUTED!");
//
//        Iterator<Path> iterator = paths.iterator();
//        while (iterator.hasNext()) {
//            String nextObj = iterator.next().toString();
//
//            // the very first query to read the tree
//            if (nextObj.equalsIgnoreCase("Objects")) {
//                logger.info("Root tree requested. Allowed.");
//                return true;
//            }
//        }
//
//        scala.collection.Iterator iter = httpRequest.cookies().iterator();
//        if (!iter.hasNext()) {
//            logger.info("No cookies!");
//            return false;
//        } else {
//
//            HttpCookie ck = null;
//            while (iter.hasNext()) {
//                HttpCookie nextCookie = (HttpCookie)iter.next();
//                logger.info(nextCookie.name() + ":" + nextCookie.content());
//
//                if (nextCookie.name().equalsIgnoreCase("JSESSIONID")) {
//                    ck = nextCookie;
//                    break;
//                }
//            }
//
//            if (ck != null) {
//
//                String requestBody = "{\"paths\":[";
//                Iterator<Path> it = paths.iterator();
//                while (it.hasNext()) {
//                    String nextObj = it.next().toString();
//
//                    // the very first query to read the tree
//                    if (nextObj.equalsIgnoreCase("Objects"))
//                        return true;
//
//                    requestBody += "\"" + nextObj + "\"";
//
//                    if (it.hasNext())
//                        requestBody += ",";
//                }
//                requestBody += "]}";
//
//                logger.info("isWrite:"+isWrite);
//                logger.info("Paths:" +requestBody);
//                return sendPermissionRequest(isWrite, requestBody, ck.toString());
//            } else
//                return false;
//        }
//    }

    @Override
    public AuthorizationResult isAuthorizedForType(spray.http.HttpRequest httpRequest,
                                boolean isWrite,
                                java.lang.Iterable<Path> paths) {

        logger.info("isAuthorizedForType EXECUTED!");

        boolean authenticated = false;

        scala.collection.Iterator iter = httpRequest.headers().iterator();

        String subjectInfo = null;
        boolean success = false;


        // First try authenticate user by certificate
        while (iter.hasNext()) {

            HttpHeader nextHeader = (HttpHeader)iter.next();
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
            logger.info("Received user certificate, data:\nemailAddress="+subjectInfo+"\nvalidated="+success);
        }

        // If there is not certificate present we try to find the session cookie
        if (!authenticated) {

            iter = httpRequest.cookies().iterator();
            if (!iter.hasNext()) {
                logger.info("No cookies!");

                // No cookies - deny request
                return Unauthorized.instance();
            } else {

                HttpCookie ck = null;
                while (iter.hasNext()) {
                    HttpCookie nextCookie = (HttpCookie) iter.next();
                    logger.info(nextCookie.name() + ":" + nextCookie.content());

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
        }

        // Check if we succeed to authenticate by session cookie
        if (authenticated) {

            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                String nextObj = iterator.next().toString();

                // the very first query to read the tree
                if (nextObj.equalsIgnoreCase("Objects")) {
                    logger.info("Root tree requested. forwarding to Partial API.");


                    ArrayList<Path> res_paths = (ArrayList) getAvailablePaths(subjectInfo, success);

                    if (res_paths == null)
                        return Unauthorized.instance();

                    // Check if security module return "all" means allowing all tree (administrator mode or read_all mode)
                    if (res_paths.size() == 1) {
                        String obj_path = res_paths.get(0).toString();
                        if (obj_path.equalsIgnoreCase("all"))
                            return Authorized.instance();
                    }

                    return new Partial(res_paths);
                } else
                    break;
            }

            String requestBody = "{\"paths\":[";
            Iterator<Path> it = paths.iterator();
            while (it.hasNext()) {
                String nextObj = it.next().toString();

                // the very first query to read the tree
                if (nextObj.equalsIgnoreCase("Objects"))
                    return Authorized.instance();

                requestBody += "\"" + nextObj + "\"";

                if (it.hasNext())
                    requestBody += ",";
            }

            if (success)
                requestBody += "],\"user\":\"" + subjectInfo + "\"}";
            else
                requestBody += "]}";

            logger.info("isWrite:" + isWrite);
            logger.info("Paths:" + requestBody);

            return sendPermissionRequest(isWrite, requestBody, subjectInfo, success);
        } else {
            return Unauthorized.instance();
        }
    }

    public AuthorizationResult isAuthorizedForRequest(spray.http.HttpRequest httpRequest,
                                   OmiRequest omiRequest) {
        return AuthApi$class.isAuthorizedForRequest(this, httpRequest, omiRequest);
    }

    public java.lang.Iterable<Path> getAvailablePaths(String subjectInfo, boolean isCertificate) {

        HttpURLConnection connection = null;
        try {
            //Create connection
            String finalURL = authServiceURI + "?getPaths=true";
            logger.info("Sending request. URI:" + finalURL);
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

            // KEYstore part
//            InputStream trustStream = new FileInputStream("keystore.jks");
//            char[] trustPassword = "1234567890".toCharArray();
//
//            // load keystore
//            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
//            trustStore.load(trustStream, trustPassword);
//
//            TrustManagerFactory trustFactory =
//                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//            trustFactory.init(trustStore);
//            TrustManager[] trustManagers = trustFactory.getTrustManagers();
//
//            SSLContext sslContext = SSLContext.getInstance("SSL");
//            sslContext.init(null, trustManagers, null);
//            SSLContext.setDefault(sslContext);

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

            logger.info(json_paths.size()+" PATHS FOUND");

            for (int i = 0; i < json_paths.size(); i++) {
                String pathString = json_paths.get(i).getAsString();
                logger.info(pathString);
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
            logger.info("Sending request. URI:" + finalURL);
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

            logger.info("RESPONSE:"+response.toString());

            return response.toString().equalsIgnoreCase("true") ? Authorized.instance() : Unauthorized.instance();
        } catch (Exception e) {
            logger.error("During http request", e);
            return Unauthorized.instance();
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }
}
