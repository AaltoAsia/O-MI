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


/**
 * Created by romanfilippov on 14/01/16.
 */
public class AuthAPIService implements AuthApi {

    private final int authServicePort = 8009;
    private final String authServiceURI = "http://localhost:" + authServicePort + "/security/PermissionService";

//    @Override
//    public boolean isAuthorizedForType(spray.http.HttpRequest httpRequest,
//                                boolean isWrite,
//                                java.lang.Iterable<Path> paths) {
//
//        System.out.println("isAuthorizedForType EXECUTED!");
//
//        Iterator<Path> iterator = paths.iterator();
//        while (iterator.hasNext()) {
//            String nextObj = iterator.next().toString();
//
//            // the very first query to read the tree
//            if (nextObj.equalsIgnoreCase("Objects")) {
//                System.out.println("Root tree requested. Allowed.");
//                return true;
//            }
//        }
//
//        scala.collection.Iterator iter = httpRequest.cookies().iterator();
//        if (!iter.hasNext()) {
//            System.out.println("No cookies!");
//            return false;
//        } else {
//
//            HttpCookie ck = null;
//            while (iter.hasNext()) {
//                HttpCookie nextCookie = (HttpCookie)iter.next();
//                System.out.println(nextCookie.name() + ":" + nextCookie.content());
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
//                System.out.println("isWrite:"+isWrite);
//                System.out.println("Paths:" +requestBody);
//                return sendPermissionRequest(isWrite, requestBody, ck.toString());
//            } else
//                return false;
//        }
//    }

    @Override
    public AuthorizationResult isAuthorizedForType(spray.http.HttpRequest httpRequest,
                                boolean isWrite,
                                java.lang.Iterable<Path> paths) {

        System.out.println("isAuthorizedForType EXECUTED!");

        scala.collection.Iterator iter = httpRequest.cookies().iterator();
        if (!iter.hasNext()) {
            System.out.println("No cookies!");
            return Unauthorized.instance();
        } else {

            HttpCookie ck = null;
            while (iter.hasNext()) {
                HttpCookie nextCookie = (HttpCookie)iter.next();
                System.out.println(nextCookie.name() + ":" + nextCookie.content());

                if (nextCookie.name().equalsIgnoreCase("JSESSIONID")) {
                    ck = nextCookie;
                    break;
                }
            }

            if (ck != null) {

                Iterator<Path> iterator = paths.iterator();
                while (iterator.hasNext()) {
                    String nextObj = iterator.next().toString();

                    // the very first query to read the tree
                    if (nextObj.equalsIgnoreCase("Objects")) {
                        System.out.println("Root tree requested. forwarding to Partial API.");


                        ArrayList<Path> res_paths = (ArrayList)getAvailablePaths(ck.toString());
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
                requestBody += "]}";

                System.out.println("isWrite:"+isWrite);
                System.out.println("Paths:" +requestBody);

                return sendPermissionRequest(isWrite, requestBody, ck.toString());
            } else
                return Unauthorized.instance();
        }
    }

    public AuthorizationResult isAuthorizedForRequest(spray.http.HttpRequest httpRequest,
                                   OmiRequest omiRequest) {
        return AuthApi$class.isAuthorizedForRequest(this, httpRequest, omiRequest);
    }

    public java.lang.Iterable<Path> getAvailablePaths(String sessionCookie) {

        HttpURLConnection connection = null;
        try {
            //Create connection
            String finalURL = authServiceURI + "?getPaths=true";
            System.out.println("Sending request. URI:" + finalURL);
            URL url = new URL(finalURL);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/json");

            connection.setRequestProperty("Cookie", sessionCookie);

            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.connect();

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
            }


            // Parse the paths and return them
            JsonObject paths = new JsonParser().parse(response_result).getAsJsonObject();
            JsonArray json_paths = paths.getAsJsonArray("paths");

            ArrayList<Path> finalPaths = new ArrayList<>(json_paths.size());

            System.out.println(json_paths.size()+" PATHS FOUND");

            for (int i = 0; i < json_paths.size(); i++) {
                String pathString = json_paths.get(i).getAsString();
                System.out.println(pathString);
                Path nextPath = new Path(pathString);
                finalPaths.add(nextPath);
            }

            return finalPaths;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }

    public AuthorizationResult sendPermissionRequest(boolean isWrite, String body, String sessionCookie) {
        HttpURLConnection connection = null;
        try {
            //Create connection
            String writeURL = isWrite ? "true" : "false";
            String finalURL = authServiceURI + "?ac=true&write=" + writeURL;
            System.out.println("Sending request. URI:" + finalURL);
            URL url = new URL(finalURL);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/json");

            connection.setRequestProperty("Cookie", sessionCookie);

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

            System.out.println("RESPONSE:"+response.toString());

            return response.toString().equalsIgnoreCase("true") ? Authorized.instance() : Unauthorized.instance();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("EXCEPTION!");
            return Unauthorized.instance();
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
    }
}
