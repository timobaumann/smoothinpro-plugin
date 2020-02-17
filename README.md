# smoothinpro-plugin

This implements a [DialogOS](https://www.dialogos.app) plugin that can be used for incremental
speech synthesis via [InproTK](http://edoc.sub.uni-hamburg.de/informatik/volltexte/2013/186/)
in its newly designed [2.x revision](https://github.com/timobaumann/inprotk/).

You can directly build and run the plugin as part of the DialogOS environment by calling 
`./gradlew run`. Open `test.dos` for an impression of how to use the plugin's functionality.

----
there's now a ROS topic /DialogOS_stress that accepts messages of type std_msgs/Int32 . Sending
numbers between -100 and +100 changes our defined stressing behaviour that needs to be related 
to the distance between the robot and the human. Also, it currently sounds like crap.
