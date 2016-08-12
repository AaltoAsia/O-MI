import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ReplacePath {
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

    Path templatePath = currentPath.resolve("etc/conf-standalone.conf");
    Path logPath = currentPath.resolve("etc/log4j.properties");
    Path tokensPath = currentPath.resolve("etc/initial.tokens");
    Path confPath = currentPath.getParent().getParent().resolve("configs/application.conf");
    try{
      String template = new String(Files.readAllBytes(templatePath), charset);
      template = template.replaceAll("(?m)^standalone\\.home.*", "standalone.home = " + currentPath.toAbsolutePath().toString());
      Files.write(templatePath, template.getBytes(charset));

      String logString = new String(Files.readAllBytes(logPath),charset);
      logString = logString.replaceAll("(?m)^log4j\\.appender\\.warpLog\\.File=.*", "log4j.appender.warpLog.File=" + currentPath.resolve("logs/nohup.out"));
      Files.write(logPath, logString.getBytes(charset));

      String warpLog = new String(Files.readAllBytes(logPath), charset);
      warpLog = warpLog.replaceAll("(?m)^log4j\\.appender\\.warpscriptLog\\.File=.*","log4j.appender.warpscriptLog.File=" + currentPath.resolve("logs/warpscript.out"));
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

