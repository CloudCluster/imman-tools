package ccio.imman.tools.command.ssh;

import java.io.IOException;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import ccio.imman.tools.ImmanNode;
import ccio.imman.tools.SshService;
import ccio.imman.tools.command.CliCommand;
import ccio.imman.tools.command.CliException;
import ccio.imman.tools.command.CliParseException;
import jline.console.ConsoleReader;

public class CopyJarAndConfig extends SshAction{

	private static final String SERVICE_FILE = "#!/bin/bash\n" + 
			"# description: CCIO ImMan Start Stop Restart\n" + 
			"# processname: ccio-imman\n" + 
			"# chkconfig: 234 20 80\n" + 
			"\n" + 
			"case $1 in\n" + 
			"start)\n" + 
			"sh /opt/run.sh\n" + 
			";; \n" + 
			"stop)   \n" + 
			"sh /opt/stop.sh\n" + 
			";; \n" + 
			"restart)\n" + 
			"sh /opt/stop.sh\n" + 
			"sh /opt/run.sh\n" + 
			";; \n" + 
			"esac    \n" + 
			"exit 0";
	
	private static final String LOGBACK_FILE = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" + 
			"<included>\n" + 
			"\n" + 
			"	<appender name=\"STDOUT\" class=\"ch.qos.logback.core.ConsoleAppender\">\n" + 
			"		<encoder>\n" + 
			"			<pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n%ex{full}</pattern>\n" + 
			"		</encoder>\n" + 
			"	</appender>\n" + 
			"\n" + 
			"	<appender name=\"FILE\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">\n" + 
			"		<file>/opt/logs/ccio-imman.log</file>\n" + 
			"		<rollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\">\n" + 
			"			<fileNamePattern>/opt/logs/ccio-imman.%d{yyyy-MM-dd}.log.zip</fileNamePattern>\n" + 
			"			<maxHistory>10</maxHistory>\n" + 
			"		</rollingPolicy>\n" + 
			"		<encoder>\n" + 
			"			<pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n%ex{full}</pattern>\n" + 
			"		</encoder>\n" + 
			"	</appender>\n" + 
			"\n" + 
			"	<appender name=\"EMAIL\" class=\"ch.qos.logback.classic.net.SMTPAppender\">\n" + 
			"		<smtpHost>localhost</smtpHost>\n" + 
			"		<from>no-reply@cloudcluster.io</from>\n" + 
			"		<to>eugene@strokin.info</to>\n" + 
			"		<subject>[CCIO Image][####DROPLET_NAME####] %m</subject>\n" + 
			"		<layout class=\"ch.qos.logback.classic.html.HTMLLayout\" />\n" + 
			"		<filter class=\"ch.qos.logback.classic.filter.ThresholdFilter\">\n" + 
			"			<level>ERROR</level>\n" + 
			"		</filter>\n" + 
			"	</appender>\n" + 
			"<!--\n" + 
			"	<logger name=\"ccio.imman\" level=\"DEBUG\" additivity=\"false\">\n" + 
			"		<appender-ref ref=\"FILE\" />\n" + 
			"	</logger> \n" + 
			"-->\n" + 
			"	<root level=\"INFO\">\n" + 
			"<!--		<appender-ref ref=\"STDOUT\" /> -->\n" + 
			"		<appender-ref ref=\"FILE\" />\n" + 
			"<!--		<appender-ref ref=\"EMAIL\" /> -->\n" + 
			"	</root>\n" + 
			"\n" + 
			"</included>";
	
	private static final String RUN_SCRIPT_TEMPLATE="#!/bin/sh\n"
			+ "echo \"Starting CCIO ImMan\"\n"
			+ "ulimit -n 65535\n"
			+ "nohup java -server -Xmx400m -Xms200m"
			+ " -XX:+HeapDumpOnOutOfMemoryError"
			+ " -jar /opt/imman-node.jar > /opt/logs/out.log 2>&1 &\n";
	
	private static final String STOP_SCRIPT_TEMPLATE="#!/bin/bash\n" + 
			"pid=`pidof java`\n" + 
			"echo \"Stopping CCIO ImMan:\" $pid\n" + 
			"kill $pid";
	
	private String pathToJar;
	
	public CopyJarAndConfig() {
		super("copy-jar", "pathToJar");
	}

	@Override
	public CliCommand parse(String[] cmdArgs, ConsoleReader console) throws CliParseException {
		if(cmdArgs == null || cmdArgs.length!=2){
			throw new CliParseException("requered arguments: pathToJar");
		}
		pathToJar = cmdArgs[1];
		return this;
	}

	@Override
	public boolean exec() throws CliException {
		SshService sshService=getSshService(cluster);
		
		for(ImmanNode imageNode : cluster.getImageNodes()){
			System.out.println("#################################################");
			Session session=null;
			try {
				session = sshService.openNewSession(imageNode.getPublicIp());
				if(session!=null){
					System.out.println("Copying "+pathToJar+" to "+imageNode.getDropletName());
					boolean res=sshService.copyFileToRemoteServer(pathToJar, "/opt/imman-node.jar", session);
					if(!res){
						System.out.println("ERROR: Cannot copy imman-node.jar to "+imageNode.getDropletName()+" || "+imageNode.getPublicIp());
					}
					
					System.out.println("Copying /opt/ccio-imman-logback.xml to "+imageNode.getDropletName());
					res=sshService.copyToRemoteServer(LOGBACK_FILE.replace("####DROPLET_NAME####", imageNode.getDropletName()), "/opt/ccio-imman-logback.xml", session);
					if(!res){
						System.out.println("ERROR: Cannot copy ccio-imman-logback.xml to "+imageNode.getDropletName()+" || "+imageNode.getPublicIp());
					}
					
					System.out.println("Copying /opt/run.sh to "+imageNode.getDropletName());
					res=sshService.copyToRemoteServer(RUN_SCRIPT_TEMPLATE, "/opt/run.sh", session);
					if(res){
						StringBuffer respMsg=new StringBuffer();
						int resCode = sshService.execute("chmod 744 /opt/run.sh", session, respMsg);
						if(resCode!=0){
							System.out.println("Cannot set permissions on "+imageNode.getDropletName()+" with message: "+respMsg);
						}else{
							System.out.println("Permissions are set");
						}
					}else{
						System.out.println("Cannot copy run.sh to "+imageNode.getDropletName());
					}
					
					System.out.println("Copying /opt/stop.sh to "+imageNode.getDropletName());
					res=sshService.copyToRemoteServer(STOP_SCRIPT_TEMPLATE, "/opt/stop.sh", session);
					if(res){
						StringBuffer respMsg=new StringBuffer();
						int resCode = sshService.execute("chmod 744 /opt/stop.sh", session, respMsg);
						if(resCode!=0){
							System.out.println("Cannot set permissions on "+imageNode.getDropletName()+" with message: "+respMsg);
						}else{
							System.out.println("Permissions are set");
						}
					}else{
						System.out.println("Cannot copy stop.sh to "+imageNode.getPublicIp());
					}
					
					System.out.println("Copying /etc/ccio-imman to "+imageNode.getDropletName());
					res=sshService.copyToRemoteServer(SERVICE_FILE, "/etc/init.d/ccio-imman", session);
					if(res){
						StringBuffer respMsg=new StringBuffer();
						int resCode = sshService.execute("chmod 755 /etc/init.d/ccio-imman; chkconfig --add /etc/init.d/ccio-imman; chkconfig --level 234 /etc/init.d/ccio-imman on; service ccio-imman start", session, respMsg);
						if(resCode!=0){
							System.out.println("Cannot set service on "+imageNode.getDropletName()+" with message: "+respMsg);
						}else{
							System.out.println("Service is set");
						}
					}else{
						System.out.println("Cannot copy /etc/ccio-imman to "+imageNode.getDropletName());
					}
				}else{
					System.out.println("Cannot connect to "+imageNode.getDropletName()+" || "+imageNode.getPublicIp());
				}
			} catch (JSchException | IOException e) {
				e.printStackTrace();
			}finally{
				if(session!=null){
					session.disconnect();
				}
			}
		}
		return true;
	}

}
