package cd.test;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Reference extends Remote {
	public final String PARSE_FAILURE = "ParseFailure";
	public final String SEMANTIC_PASSED = "OK\n";
	public String parserReference(String fileText) throws RemoteException;
	public String semanticReference(String fileText) throws RemoteException;
	public String execReference(String fileText, String inputText) throws RemoteException;
	public String cfgReference(String fileText) throws RemoteException;
	public String optReference(String fileText, String inputText) throws RemoteException;
}
