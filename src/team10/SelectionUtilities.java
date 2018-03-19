package team10;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class SelectionUtilities {

	// compares a column's value to an argument using an operator
	// returns the boolean result of the comparison
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected static boolean compareColumnToArgumentUsingOperator(Object columnValue, Object argument, String operator)
			throws DBAppException {
		boolean result = false;

		Comparable firstArgument = (Comparable) columnValue;
		Comparable secondArgument = (Comparable) argument;

		switch (operator) {
		case ">":
			result = firstArgument.compareTo(secondArgument) > 0;
			break;
		case ">=":
			result = firstArgument.compareTo(secondArgument) >= 0;
			break;
		case "<":
			result = firstArgument.compareTo(secondArgument) < 0;
			break;
		case "<=":
			result = firstArgument.compareTo(secondArgument) <= 0;
			break;
		default:
			throw new DBAppException("Invalid operator during selection.");
		}

		return result;
	}

	// returns the column name of the primary key
	protected static String getPrimaryKeyColumnName(String tableName) {
		String line = null;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("data/metadata.csv"));
			line = br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}

		String primaryKey = null;

		while (line != null) {
			String[] content = line.split(",");

			if (content[0].equals(tableName)) {
				if ((content[3].toLowerCase()).equals("true"))
					primaryKey = content[1];
			}
			try {
				line = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return primaryKey;
	}

	// checks what columns are indexed and returns their names
	protected static ArrayList<String> getIndexedColumns(String tableName) {

		// the array list to return
		ArrayList<String> indexedColumns = new ArrayList<>();

		String line = null;
		BufferedReader br = null;

		try {
			br = new BufferedReader(new FileReader("data/metadata.csv"));
			line = br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}

		while (line != null) {
			String[] content = line.split(",");

			if (content[0].equals(tableName)) {
				if (content[4].toLowerCase().equals("true"))
					indexedColumns.add(content[1]);
			}

			try {
				line = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return indexedColumns;
	}

	public Iterator selectFromTable(String strTableName, String strColumnName, Object[] objarrValues,
			String[] strarrOperators) throws DBAppException {

		ArrayList<Object> output = new ArrayList<Object>();
		int tablePageNumber = 1;

		while (true) {

			Page page = PageManager.loadPageIfExists("data/" + strTableName + "/page_" + tablePageNumber++ + ".ser");

			if (page == null)
				break;

			for (int i = 0; i < page.getMaxRows() && page.getRows()[i] != null; i++);
				

		}

		return output.iterator();

	}

	protected static boolean isValueInResultSet(Object columnValue, Object[] objarrValues, String[] strarrOperators)
			throws DBAppException {
		
		return isValueInResultSet(columnValue, objarrValues, strarrOperators, 0);

	}

	protected static boolean isValueInResultSet(Object columnValue, Object[] objarrValues, String[] strarrOperators,
			int index) throws DBAppException {

		if (index == objarrValues.length)
			return true;
		else
			return compareColumnToArgumentUsingOperator(columnValue, objarrValues[index], strarrOperators[index])
					&& isValueInResultSet(columnValue, objarrValues, strarrOperators, ++index);

	}

}
