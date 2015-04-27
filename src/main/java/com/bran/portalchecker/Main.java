package com.bran.portalchecker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

/**
 * Selenium WebDriver script that checks for any new messages or non-future
 * charges on MyUCSC
 * 
 * `portalchecker.config` file is expected in the root directory (with pom.xml)
 * that lists the necessary credentials for accessing MyUCSC and an email
 * account (to send notifications)
 * 
 * `portalchecker.chargehistory` file is created in the root directory. The user
 * shouldn't need to edit it at all.
 *
 */
public class Main {
	// Files used
	private static final String CONFIG_FILE = "portalchecker.config";
	private static final String CHARGE_HISTORY_FILE = "portalchecker.chargehistory";
	private static final String MESSAGE_HISTORY_FILE = "portalchecker.messagehistory";
	private static final String LOG_FILE = "portalchecker.log";

	// Login Credentials
	private static String portalUsername;
	private static String portalPassword;
	private static String emailAddress;
	private static String emailPassword;

	// Mail Setup
	private static String HOST = "smtp.gmail.com";
	private static String PORT = "465";
	
	/*
	 * Main routine: Navigates to, logs into, and the checks MyUCSC for new messages or charges.
	 * If new messages or charges are found a notification email is sent. Log is updated;
	 */
	public static void main(String[] args) {
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
		WebElement messageTable = driver.findElement(By
				.className("PSLEVEL1GRID"));
		processTable(messageTable, MESSAGE_HISTORY_FILE);
		// Navigate to "My Student Center"
		driver.switchTo().defaultContent();
		driver.findElement(By.id("pthnavbca_MYFAVORITES")).click();
		driver.findElement(By.linkText("My Student Center")).click();
		driver.switchTo().frame("TargetContent");
		// Check "Charges By Due Date"
		driver.findElement(By.id("DERIVED_SSS_SCL_SS_CHRG_DUE_LINK")).click();
		WebElement chargeTable = driver.findElement(By
				.className("PSLEVEL1GRID"));
		processTable(chargeTable, CHARGE_HISTORY_FILE);
		driver.quit();
		updateLog();
	}

	private static void updateLog() {
		FileWriter logger;
		try {
			//new File(LOG_FILE).createNewFile();
			logger = new FileWriter(LOG_FILE, true);
			logger.write(String.format("Executed on %s%n", new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date())));
			logger.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Given a WebElement corresponding to either the MyUCSC "Messages" or
	 * "Charges Due" table, read the contents accordingly and if any of the rows
	 * haven't been seen before, notify the user. Once finished, update history
	 * for later executions.
	 */
	private static void processTable(WebElement table, String historyPath) {
		List<String> history = readHistoryFile(historyPath);
		List<String> itemsRead = new ArrayList<String>();
		for (WebElement elem : table.findElements(By.tagName("tr"))) {
			if (elem.getAttribute("id").contains("row")) { // is a valid entry
															// in table
				String item = (historyPath == CHARGE_HISTORY_FILE) ? item = chargeFromRow(elem)
						: messageFromRow(elem);
				itemsRead.add(item);
				if (!history.contains(item)) {
					if (historyPath == CHARGE_HISTORY_FILE)
						notifyNewCharge(item);
					else
						notifyNewMessage(item);
				}
			}
		}
		writeHistoryFile(historyPath, itemsRead);
	}

	/*
	 * Given a <tr> WebElement, construct the String representing a message as
	 * listed in the history file.
	 */
	private static String messageFromRow(WebElement elem) {
		List<WebElement> cells = elem.findElements(By.tagName("td"));
		String from = cells.get(1).getText().trim();
		String expiration = cells.get(4).getText().trim();
		String subject = cells.get(5).getText().trim();
		return String.format("Subject: %s; From: %s; Expires: %s", subject,
				from, expiration);
	}

	/*
	 * Given a <tr> WebElement, construct the String representing a charge as
	 * listed in the history file.
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
		sendEmail("PortalChecker: New Charge Found", String.format("A new message was found:%n%n\"%s\"", message));
	}

	/*
	 * Notify the user that a new charge has been found
	 */
	private static void notifyNewCharge(String charge) {
		sendEmail("PortalChecker: New Charge Found", String.format("A new charge was found:%n%n\"%s\"", charge));
	}

	/*
	 * Return a List of all the lines
	 */
	private static List<String> readHistoryFile(String path) {
		List<String> history = new ArrayList<String>();
		File historyFile = new File(path);
		// Create empty history file if it doesn't already exist
		try {
			if (historyFile.createNewFile())
				return history;
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Populate chargeHistory with the lines listed in the history file
		Scanner scanner = null;
		try {
			scanner = new Scanner(historyFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		while (scanner.hasNext())
			history.add(scanner.nextLine());
		scanner.close();
		return history;
	}

	/*
	 * Overwrite the history file with the contents of the given List
	 */
	private static void writeHistoryFile(String path, List<String> history) {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(path);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		for (String item : history)
			writer.println(item);
		writer.close();
	}

	/*
	 * Get email address and password and MyUCSC username and password from
	 * config file. If the file doesn't exist, ask the user for credentials and
	 * create it for them.
	 */
	private static void getCredentials() {
		File configFile = new File(CONFIG_FILE);
		Scanner configInfo = null;
		if (configFile.exists() && !configFile.isDirectory()) { // config file
																// exists
			try {
				configInfo = new Scanner(configFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			while (configInfo.hasNext()) {
				String word = configInfo.next();
				if (word.charAt(0) == '#')
					configInfo.nextLine(); // comment; skip line
				if (word.equals("myucsc-username"))
					portalUsername = configInfo.next();
				if (word.equals("myucsc-password"))
					portalPassword = configInfo.next();
				if (word.equals("email-address"))
					emailAddress = configInfo.next();
				if (word.equals("email-password"))
					emailPassword = configInfo.next();
			}
		} else {
			// Get credentials from user
			configInfo = new Scanner(System.in);
			System.out.println("Please enter your MyUCSC username:");
			portalUsername = configInfo.nextLine().trim();
			System.out.println("Please enter your MyUCSC password:");
			portalPassword = configInfo.nextLine().trim();
			System.out
					.println("Please enter your email address (e.g. \"benjaminran2@gmail.com\":");
			emailAddress = configInfo.nextLine().trim();
			System.out.println("Please enter your email account password:");
			emailPassword = configInfo.nextLine().trim();
			// Save credentials in config file
			PrintWriter writer = null;
			try {
				writer = new PrintWriter(CONFIG_FILE);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			writer.println("myucsc-username " + portalUsername);
			writer.println("myucsc-password " + portalPassword);
			writer.println("email-address " + emailAddress);
			writer.println("email-password " + emailPassword);
			writer.close();
		}
		configInfo.close();
	}

	public static void sendEmail(String subject, String body) {
		// Use Properties object to set environment properties
		Properties props = new Properties();

		props.put("mail.smtp.host", HOST);
		props.put("mail.smtp.port", PORT);
		props.put("mail.smtp.user", emailAddress);

		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.debug", "true");

		props.put("mail.smtp.socketFactory.port", "465");
		props.put("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		props.put("mail.smtp.socketFactory.fallback", "false");
		// Obtain the default mail session
		Session session = Session.getDefaultInstance(props,
				new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(emailAddress,
								emailPassword);
					}
				});
		// Construct the mail message
		MimeMessage message = new MimeMessage(session);
		try {
			message.setText(body);
			message.setSubject(subject);
			message.setFrom(new InternetAddress(emailAddress));
			message.addRecipient(RecipientType.TO, new InternetAddress(
					emailAddress));
			message.saveChanges();
			// Deliver the message
			Transport transport = session.getTransport("smtps");
			transport.connect(HOST, emailAddress, emailPassword);
			transport.sendMessage(message, message.getAllRecipients());
			transport.close();
		} catch (MessagingException e) {
			e.printStackTrace();
		}
	}
}
