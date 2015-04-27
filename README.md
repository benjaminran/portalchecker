# portalchecker
A Selenium WebDriver script to check whether new messages or charges have been posted to MyUCSC and notify the user via email if they have

## Overview
Selenium WebDriver script that checks for any new messages or non-future charges on MyUCSC. Files are used to persist state between executions.
* `portalchecker.config` file is expected in the working directory that lists the necessary credentials for accessing MyUCSC and an email account (to send notifications)
  * note: username/password for MyUCSC and an email account are stored totaly openly in this file. The script should only be deployed in a private directory.
* `portalchecker.chargehistory` file is created in the working directory. The user shouldn't need to edit it at all.    
* `portalchecker.messagehistory` file is created in the working directory. The user shouldn't need to edit it at all.
