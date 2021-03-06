package team10;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

public class CreationUtilities {
	protected static boolean checkValidName(String strTableName) {
		// A more robust approach would be check the directories and metadata.csv the same time
		File dataDirectory = new File("data");
		File[] fileList = dataDirectory.listFiles();
		ArrayList<String> tableNames = new ArrayList<String>();
		for (File file : fileList) {
			if (file.isDirectory()) {
				tableNames.add(file.getName());
			}
		}
		if (tableNames.contains(strTableName)) {
			return false;
		} else {
			return true;
		}

	}

	protected static boolean checkValidKeys(Hashtable<String, String> htblColNameType) {
		String[] supportedTypes = { "java.lang.Integer", "java.lang.String", "java.lang.Double", "java.lang.Boolean",
				"java.util.Date" };
		// get all the keys from the hashtable
		Enumeration<String> htblKeys = htblColNameType.keys();
		// iterate over the keys
		while (htblKeys.hasMoreElements()) {
			String key = htblKeys.nextElement();
			// if a type is not supported return false
			if (!arrayContains(supportedTypes, htblColNameType.get(key)))
				return false;
		}
		// if all types are supported, passed validation
		return true;
	}

	protected static boolean arrayContains(String[] array, String key) {
		for (String element : array) {
			if (key.equals(element))
				return true;
		}
		return false;
	}

	// check metadata.csv file exists
	protected static boolean checkMeta() {
		File metaFile = new File("data/metadata.csv");
		if (metaFile.exists()) {
			return true;
		}
		return false;
	}

	// create a metadata.csv file
	protected static void createMeta() throws IOException {
		File metaFile = new File("data/metadata.csv");
		metaFile.createNewFile();
	}

	// add meta data of table to metadaata.csv file
	protected static void addMetaData(String strTableName, String strClusteringKeyColumn,
			Hashtable<String, String> htblColNameType) throws DBAppException, IOException {
		if (!checkValidClusteringKey(strClusteringKeyColumn, htblColNameType)) {
			throw new DBAppException();
		}
		Enumeration<String> htblKeys = htblColNameType.keys();
		FileWriter metaWriter = new FileWriter("data/metadata.csv", true);
		while (htblKeys.hasMoreElements()) {
			String colName = htblKeys.nextElement();
			String isIndexed = "false";
			String isClusteringKey = "";
			if (colName.equals(strClusteringKeyColumn))
				isClusteringKey = "true";
			else
				isClusteringKey = "false";
			String type = htblColNameType.get(colName);
			metaWriter.write(strTableName + "," + colName + "," + type + "," + isClusteringKey + "," + isIndexed + "\n");

		}
		metaWriter.close();
	}

	// check whether the clustering key exists in the table declaration
	protected static boolean checkValidClusteringKey(String strClusteringKeyColumn, Hashtable<String, String> htblColNameType) {
		Enumeration<String> htblKeys = htblColNameType.keys();
		while (htblKeys.hasMoreElements()) {
			String key = htblKeys.nextElement();
			if (key.equals(strClusteringKeyColumn)) {
				return true;
			}
		}
		return false;
	}

	// add a new Directory
	protected static void addDirectory(String directoryName, String path) throws DBAppException {
		File directory = new File(path + "/" + directoryName);
		if (!directory.mkdir()) {
			throw new DBAppException();
		}

	}
	

}
