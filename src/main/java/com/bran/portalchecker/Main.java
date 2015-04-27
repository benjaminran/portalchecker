package com.bran.portalchecker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

/**
 * Selenium WebDriver script that checks for any new messages or non-future charges on MyUCSC
 * 
 * `portalchecker.config` file is expected in the root directory (with pom.xml) that lists the 
 * necessary credentials for accessing MyUCSC and an email account (to send notifications)
 * 
 * `portalchecker.chargehistory` file is created in the root directory. The user shouldn't need
 * to edit it at all.
 *
 */
public class Main {
	private static final String CONFIG_FILE = "portalchecker.config";
	private static final String CHARGE_HISTORY_FILE = "portalchecker.chargehistory";
	private static final String MESSAGE_HISTORY_FILE = "portalchecker.messagehistory";
	
	private static String portalUsername;
    private static String portalPassword;
    private static String emailAddress;
    private static String emailPassword;
	
	public static void main( String[] args ) {
        // Navigate to MyUCSC and log in
        getCredentials();
        WebDriver driver = new FirefoxDriver();
        driver.get("https://my.ucsc.edu");
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        driver.findElement(By.id("userid")).sendKeys(portalUsername);
        driver.findElement(By.id("pwd")).sendKeys(portalPassword);
        driver.findElement(By.name("Submit")).submit();
        // Check "Messages"
        driver.findElement(By.id("SJ_PA_DERIVED_DESCR20")).click();
        driver.switchTo().frame("TargetContent");
        List<String> messageHistory = readHistoryFile(MESSAGE_HISTORY_FILE);
        List<String> messagesRead = new ArrayList<String>();
        WebElement messageTable = driver.findElement(By.className("PSLEVEL1GRID"));
        for(WebElement elem : messageTable.findElements(By.tagName("tr"))) {
        	if(elem.getAttribute("id").contains("row")) { // is a valid entry in table
        		//if(elem.getText().contains("Future")) continue; // is due in "future"; ignore
        		String message = messageFromRow(elem);
        		messagesRead.add(message);
        		if(!messageHistory.contains(message)) notifyNewMessage(message);
        	}
        }
        writeHistoryFile(MESSAGE_HISTORY_FILE, messagesRead);
        // Navigate to "My Student Center"
        driver.switchTo().defaultContent();
        driver.findElement(By.id("pthnavbca_MYFAVORITES")).click();
        driver.findElement(By.linkText("My Student Center")).click();
        driver.switchTo().frame("TargetContent");
        // Check "Charges By Due Date"
        driver.findElement(By.id("DERIVED_SSS_SCL_SS_CHRG_DUE_LINK")).click();
		List<String> chargeHistory = readHistoryFile(CHARGE_HISTORY_FILE);
        List<String> chargesRead = new ArrayList<String>();
        WebElement chargeTable = driver.findElement(By.className("PSLEVEL1GRID"));
        for(WebElement elem : chargeTable.findElements(By.tagName("tr"))) {
        	if(elem.getAttribute("id").contains("row")) { // is a valid entry in table
        		//if(elem.getText().contains("Future")) continue; // is due in "future"; ignore
        		String charge = chargeFromRow(elem);
        		chargesRead.add(charge);
        		if(!chargeHistory.contains(charge)) notifyNewCharge(charge);
        	}
        }
        writeHistoryFile(CHARGE_HISTORY_FILE, chargesRead);
        driver.quit();
    }

	/* 
	 * Given a <tr> WebElement, construct the String representing a message as listed in the history file.
	 */
	private static String messageFromRow(WebElement elem) {
		List<WebElement> cells = elem.findElements(By.tagName("td"));
		String from = cells.get(1).getText().trim();
		String expiration = cells.get(4).getText().trim();
		String subject = cells.get(5).getText().trim();
		return String.format("Subject: %s; From: %s; Expires: %s", subject, from, expiration);
	}

	/* 
	 * Given a <tr> WebElement, construct the String representing a charge as listed in the history file.
	 */
	private static String chargeFromRow(WebElement elem) {
		List<WebElement> cells = elem.findElements(By.tagName("td"));
		String amount = cells.get(1).getText().trim();
		String due = cells.get(0).getText().trim();
		return String.format("$%s due %s", amount, due);
	}
	
	/*
	 * Notify the user that a new message has been found
	 */
	private static void notifyNewMessage(String message) {
		System.out.println(String.format("A new message of \"%s\" was found!", message));
	}
	
	/*
	 * Notify the user that a new charge has been found
	 */
	private static void notifyNewCharge(String charge) {
		System.out.println(String.format("A new charge of \"%s\" was found!", charge));
	}

	/*
	 * Return a List of all the lines
	 */
	private static List<String> readHistoryFile(String path) {
		List<String> history = new ArrayList();
		File historyFile = new File(path);
		// Create empty history file if it doesn't already exist
		try { if(historyFile.createNewFile()) return history; }
		catch (IOException e) {e.printStackTrace(); }
		// Populate chargeHistory with the lines listed in the history file
		Scanner scanner = null;
		try { scanner = new Scanner(historyFile); }
		catch (FileNotFoundException e) { e.printStackTrace(); }
		while(scanner.hasNext()) history.add(scanner.nextLine());
		scanner.close();
		return history;
	}
	
	/* 
	 * Overwrite the history file with the contents of the given List
	 */
	private static void writeHistoryFile(String path, List<String> history) {
		PrintWriter writer = null;
		try { writer = new PrintWriter(path); }
		catch (FileNotFoundException e) {e.printStackTrace(); }
    	for(String item : history) writer.println(item);
    	writer.close();
	}
	
	/*
	 * Get email address and password and MyUCSC username and password from config file.
	 * If the file doesn't exist, ask the user for credentials and create it for them.
	 */
	private static void getCredentials() {
        File configFile = new File(CONFIG_FILE); 
        Scanner configInfo = null;
        if(configFile.exists() && !configFile.isDirectory()) { // config file exists
        	try {
    			configInfo = new Scanner(configFile);
    		} catch (FileNotFoundException e) {
    			e.printStackTrace();
    		}
            while(configInfo.hasNext()) {
            	String word = configInfo.next();
            	if(word.charAt(0)=='#') configInfo.nextLine(); // comment; skip line
            	if(word.equals("myucsc-username")) portalUsername = configInfo.next();
            	if(word.equals("myucsc-password")) portalPassword = configInfo.next();
            	if(word.equals("email-address")) emailAddress = configInfo.next();
            	if(word.equals("email-password")) emailPassword = configInfo.next();
            }
        }
        else {
        	// Get credentials from user
        	configInfo = new Scanner(System.in);
        	System.out.println("Please enter your MyUCSC username:");
        	portalUsername = configInfo.nextLine().trim();
        	System.out.println("Please enter your MyUCSC password:");
        	portalPassword = configInfo.nextLine().trim();
        	System.out.println("Please enter your email address (e.g. \"benjaminran2@gmail.com\":");
        	emailAddress = configInfo.nextLine().trim();
        	System.out.println("Please enter your email account password:");
        	emailPassword = configInfo.nextLine().trim();
        	// Save credentials in config file
        	PrintWriter writer = null;
			try { writer = new PrintWriter(CONFIG_FILE); }
			catch (FileNotFoundException e) {e.printStackTrace(); }
        	writer.println("myucsc-username "+portalUsername);
        	writer.println("myucsc-password "+portalPassword);
        	writer.println("email-address "+emailAddress);
        	writer.println("email-password "+emailPassword);
        	writer.close();
        }
        configInfo.close();
	}
}
