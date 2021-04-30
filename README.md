# HydratorV2
Hydrator is a Java tool for hydrating large datasets of tweet ids in a relatively short time.
It supports OAUTH1 authentication and OAUTH2 bearer tokens and is capable of handling twitter's rate limits on its own.
Usage
The executable jar will take 6 argumentss 
fileId --> a file containg a list (one for each line) of files (absolute paths) each composed of one tweet id per line 
tokensFile --> an xml file like the one in github
logFolder --> folder to store the app's logs
saveFolder --> folder where you want the hydrated tweets to be saved (saved in compressed gz format)
config.xmlPath --> path to the config xml file in github (log config)
parsingVelocity --> [SLOW, FAST, VERY_FAST, MAX] it determines the speed (by adjusting the buffer sizes of each component) at which the hydrator will make requests to twitter's api and scale accordingly if it's too fast (it can only scale down for the moment)
