FJDetector
===========

**WARNING:** This is a research project.

This tool is aimed to detect and refactor a particular misuse of the ForkJoin framework. 

FJDetector is implemented as an Eclipse plug-in. It is a fork of [CTADetector](http://mir.cs.illinois.edu/~yulin2/CTADetector/), from the folks of University of Illinois. It extends Eclipse's refactoring engine. It contains two parts: detecting and refactoring.

It requires that you use Eclipse (version >= 4.2, Juno with jre >= 1.7). 

Usage (source code)
-------------------

First, you need to Download Eclipse Plug-in.

- Go to "Help", then go to "Install New Software", and the install dialog pops up.
- Click on the "Available Software Sites" link on the install dialog.
- Check on or copy "Eclipse Luna Update Site http://download.eclipse.org/releases/luna Enabled" or just skip step 2 and copy and paste this link "http://download.eclipse.org/releases/luna"
- Add it and it gives your a list of softwares, go to "Programming Languages" and choose "Eclipse Java Development Tool"
- Restart your Eclipse

Second, download and import FJDetector source code as a "Plug-ins and Fragments" project.


Usage (binary)
--------------

- Copy the .jar file inside your *eclipse/plugin* directory