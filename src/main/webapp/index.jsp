<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
	<head>
		<title>Pentaho Reporting 5 Simple Service</title>

		<style>
			body, html {
				margin: 0;
				padding: 0;
			}

			#content {
				margin: 10px 10px 0 10px;
				padding: 20px;
				border-radius: 6px;
				background-color: #eee;
			}
		</style>
	</head>

	<body>
		<div id="content">
			<h4>Security</h4>

			<p>
				This service should be deployed in a secure environment. There is no authentication support for
				the first version of this service and therefore it should be put behind a firewall or only locally
				reachable and consumed by your application.
			</p>

			<h4>Libraries needed</h4>

			<p>
				The first libraries we need to load are the Pentaho Report Designer ones. We also need
				the JDBC drivers that users will make use of. A library for encoding/decoding JSON is also
				needed, this webapp uses <code>https://code.google.com/p/json-simple</code>.
			</p>

			<h4>JNDI Connections</h4>

			<p>
				Pentaho reports will usually make use of JNDI connections, and to create a named JNDI connection
				available to reports, you should modify the file located in <code>META-INF/context.xml</code>.
				There is an example JNDI connection configuration which can be used as a base template for more
				connections.
			</p>

			<h4>Compiling</h4>

			<p>
				There is only need to compile 2 files: <code>com.prd.service.listeners/InitListener.java</code> and
				<code>com.prd.service.servlets/ReportRunner.java</code>, when compiling this 2 classes, it will be
				needed to append the <code>META-INF/lib</code> to its classpath, as well as the <code>servlet-api.jar</code>
				library that comes with servlet containers.
			</p>
		</div>
	</body>
</html>