import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.net.URL;

public class ReplacePath {
        public static void main2(String[] args){
            String inputPath = "";
            //Charset charset = StandardCharsets.UTF_8;
            if (args.length > 0){
                inputPath = args[0];
            } else {
                System.err.println("give path to warp10 directory as parameter");
                System.exit(1);
            }
            String tarGzFilename = "warp10.tar.gz";

            Path currentPath = Paths.get(inputPath).getParent().resolve(tarGzFilename);//.resolve("bin" + File.separator + tarGzFilename);
            Path warp10Location = Paths.get(inputPath);
            URL url = new URL("https://bintray.com/cityzendata/generic/download_file?file_path=io%2Fwarp10%2Fwarp10%2F1.0.16%2Fwarp10-1.0.16.tar.gz");
            File dest = currentPath.toFile();
            org.apache.commons.io.FileUtils.copyURLToFile(url, dest);

            String fileName = dest.toString();
            String tarFilename= fileName + ".tar";
            FileInputStream inStream = new FileInputStream(dest);
            GZIPInputStream gzInStream = new GZIPInputStream(inStream);
            FileOutputStream outStream = new FileOutputStream(tarFilename);

            byte[] buf = new byte[1024];
            int len;
            while((len = gzInStream.read(buf)) > 0)
            {
                outStream.write(buf, 0, len);
            }

            gzInStream.close();
            outStream.close();

            TarArchiveInputStream tarInputStream = new TarArchiveInputStream(new FileInputStream(tarFilename));
            TarArchiveEntry entry = null;
            int offset = 0;
            FileOutputStream outputFile = null;

            while((entry = tarInputStream.getNextTarEntry()) != null) {
                File outputDir = warp10Location.resolve(entry.getName()).toFile();
                if(! outputDir.getParentFile().exists()){
                    outputDir.getParentFile().mkdirs();
                }

                if(entry.isDirectory()){
                    outputDir.mkdirs();
                } else {
                    byte[] content = new  byte[(int) entry.getSize()];
                    offset = 0;
                    tarInputStream.read(content,offset,content.length - offset);
                    outputFile = new FileOutputStream(outputDir);
                    IOUtils.write(content, outputFile);
                    outputFile.close();

                }
            }
            tarInputStream.close();
            File tarFile = new File(tarFilename);
            tarFile.delete();


        }


  public static void main(String[] args) {
    String inputPath = "";
    Charset charset = StandardCharsets.UTF_8;
    if (args.length > 0){
      inputPath = args[0];
    } else {
      System.err.println("give path to warp10 directory as parameter");
      System.exit(1);
    }
    Path currentPath = Paths.get(inputPath);

    Path templatePath = currentPath.resolve("etc" + File.separator + "conf-standalone.conf");
    Path logPath = currentPath.resolve("etc" + File.separator + "log4j.properties");
    Path tokensPath = currentPath.resolve("etc" + File.separator + "initial.tokens");
    Path confPath = currentPath.getParent().getParent().resolve("configs" + File.separator + "application.conf");
    try{
      String template = new String(Files.readAllBytes(templatePath), charset);
      template = template.replaceAll("(?m)^standalone\\.home.*", "standalone.home = " + currentPath.toAbsolutePath().toString().replace('\\', '/'));
      Files.write(templatePath, template.getBytes(charset));

      String logString = new String(Files.readAllBytes(logPath),charset);
      logString = logString.replaceAll("(?m)^log4j\\.appender\\.warpLog\\.File=.*", "log4j.appender.warpLog.File=" + currentPath.resolve("logs" + File.separator + "nohup.out").toAbsolutePath().toString().replace('\\', '/'));
      Files.write(logPath, logString.getBytes(charset));

      String warpLog = new String(Files.readAllBytes(logPath), charset);
      warpLog = warpLog.replaceAll("(?m)^log4j\\.appender\\.warpscriptLog\\.File=.*","log4j.appender.warpscriptLog.File=" + currentPath.resolve("logs" + File.separator + "warpscript.out").toAbsolutePath().toString().replace('\\', '/'));
      Files.write(logPath, warpLog.getBytes(charset));

      String tokens = new String(Files.readAllBytes(tokensPath), charset);
      String readToken = "";
      String writeToken = "";

      Pattern readTokenPattern = Pattern.compile("read\":\\{\"token\":\"([^\"]*)");
      Matcher readTokenMatcher = readTokenPattern.matcher(tokens);

      if(readTokenMatcher.find()){
        readToken = readTokenMatcher.group(1);
      } else {
        System.err.println("no read token found");
        //System.exit(1);
      }

      Pattern writeTokenPattern = Pattern.compile("write\":\\{\"token\":\"([^\"]*)");
      Matcher writeTokenMatcher = writeTokenPattern.matcher(tokens);

      if(writeTokenMatcher.find()){
        writeToken = writeTokenMatcher.group(1);
      } else {
        System.err.println("no write token found");
        //System.exit(1);
      }

      String appConf1 = new String(Files.readAllBytes(confPath), charset);
      appConf1 = appConf1.replaceAll("read-token\\s*=\\s*\"[^\"]*\"", "read-token = \"" + readToken + "\"");
      Files.write(confPath, appConf1.getBytes(charset));

      String appConf2 = new String(Files.readAllBytes(confPath), charset);
      appConf2 = appConf2.replaceAll("write-token\\s*=\\s*\"[^\"]*\"", "write-token = \"" + writeToken + "\"");
      Files.write(confPath,appConf2.getBytes(charset));

    } catch (IOException ex) {
      System.err.println("invalid path: " + ex.toString());
      System.exit(1);
    }

  } 
}

