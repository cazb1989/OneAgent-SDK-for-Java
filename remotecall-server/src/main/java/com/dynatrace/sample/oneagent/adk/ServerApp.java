package com.dynatrace.sample.oneagent.adk;

/*
 * Copyright 2017 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import com.dynatrace.oneagent.adk.OneAgentADKFactory;
import com.dynatrace.oneagent.adk.api.IncomingRemoteCallTracer;
import com.dynatrace.oneagent.adk.api.OneAgentADK;
import com.dynatrace.oneagent.adk.api.enums.ADKState;

/**
 * ServerApp is listing for
 * 
 * @author Alram.Lechner
 *
 */
public class ServerApp {

	
	private final OneAgentADK oneAgentAdk;

	private ServerApp() {
		oneAgentAdk = OneAgentADKFactory.createInstance();
		oneAgentAdk.setLoggingCallback(new StdErrLoggingCallback());
		ADKState currentADKState = oneAgentAdk.getCurrentADKState();
		switch (currentADKState) {
		case ACTIVE:
			System.out.println("ADK is active and capturing.");
			break;
		case PERMANENT_INACTIVE:
			System.err.println(
					"ADK is PERMANENT_INACTIVE; Probably no agent injected or agent is incompatible with ADK.");
			break;
		case TEMPORARY_INACTIVE:
			System.err.println("ADK is TEMPORARY_INACTIVE; Agent has been deactived - check agent configuration.");
			break;
		default:
			System.err.println("ADK is in unknown state.");
			break;
		}
	}
	
	public static void main(String args[]) {
		System.out.println("*************************************************************");
		System.out.println("**       Running remote call server                        **");
		System.out.println("*************************************************************");
		int port = 33744; // default port
		for (String arg : args) {
			if (arg.startsWith("port=")) {
				port = Integer.parseInt(arg.substring("port=".length()));
			} else {
				System.err.println("unknown argument: " + arg);
			}
		}
		try {
			new ServerApp().run(port);
			System.out.println("remote call server stopped. sleeping a while, so agent is able to send data to server ...");
			Thread.sleep(15000); // we have to wait - so agent is able to send data to server.
		} catch (Exception e) {
			System.err.println("remote call server failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void run(int port) throws IOException, ClassNotFoundException {
		ServerSocket serverSocket = new ServerSocket(port);
		try {
			System.out.println("Waiting for clients no port " + serverSocket.getInetAddress().getHostName() + ":"
					+ serverSocket.getLocalPort());
			Socket client = serverSocket.accept();
			try {
				System.out.println(
						"Client " + client.getInetAddress().getHostName() + ":" + client.getPort() + " connected");
				ObjectInputStream in = new ObjectInputStream(client.getInputStream());
				Object receviedTag = in.readObject();
				System.out.println("received tag: " + receviedTag.toString());
				traceClientRequest(receviedTag);
			} finally {
				client.close();
			}
		} finally {
			serverSocket.close();
		}
	}
	
	private void traceClientRequest(Object receivedTag) {
		IncomingRemoteCallTracer externalIncomingRemoteCall = oneAgentAdk.traceExternalIncomingRemoteCall("myMethod", "myService", "endpoint");
		if (receivedTag instanceof String) {
			externalIncomingRemoteCall.setDynatraceStringTag((String) receivedTag);
		} else if (receivedTag instanceof byte[]) {
			externalIncomingRemoteCall.setDynatraceByteTag((byte[]) receivedTag);
		} else {
			System.err.println("invalid tag received: " + receivedTag.getClass().toString());
		}
		
		externalIncomingRemoteCall.start();
		try {
			handleClientRequest();
		} catch (Exception e) {
			externalIncomingRemoteCall.error(e);
		} finally {
			externalIncomingRemoteCall.end();
		}
		
	}

	private void handleClientRequest() {
		// do whatever the server should do ...
	}
	
}