FJDetector
===========

**WARNING:** This is a research project.

This tool is aimed to detect and refactor a particular misuse of the ForkJoin framework. 

FJDetector is implemented as an Eclipse plug-in, extending Eclipse's refactoring engine. It requires that you use Eclipse (version >= 4.2, Juno with jre >= 1.7).

FJDetector is a fork of [CTADetector](http://mir.cs.illinois.edu/~yulin2/CTADetector/), from the folks of University of Illinois. For more details, please check CTADetector webpage. 

Tool Demo
---------

FJDetector contains two parts: detecting and refactoring.

For detecting, one just need to click with the right button on the projects tree, and select Detect Idiom -> Detect ForkJoin Misuses. 

![menu](http://gustavopinto.org/lost+found/fjdetector-menu.png)

FJDetector will then analyze the entire project looking for places where a data structure is being copied over ForkJoin computations. The results are shown in an Eclipse window. Double click to open the file.

![results](http://gustavopinto.org/lost+found/fjdetector-results.png)

Refactoring is implemented in an automatic way. After FJDetector detects refactoring opportunities, it will open a dialog showing how the refactored code will look like. If the user agree with the transformation, he just need to click on "Ok". Cancel otherwise.

![refactoring](http://gustavopinto.org/lost+found/fjdetector-refactoring.png)

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

License
-------

FJDetector is released under Eclipse Public License