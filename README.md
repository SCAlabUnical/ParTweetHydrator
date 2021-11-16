# ParTweetHydrator
ParTweetHydrator is a Java tool for hydrating large datasets of tweet ids in a relatively short time.
It supports OAUTH1 authentication and OAUTH2 bearer tokens and is capable of handling twitter's rate limits on its own.
## Use
### Arguments:  
* **IDs**  Either a file or a directory containing text files made up of a single id per line, if you select a directory the hydrator will load all the text files in it and attempt to dehydrate them </br>
- **Token** **File**  An xml file like the one in the repository _(mock.xml)_   
- **Log Folder** the folder that will store the app's logs  
- **Save Folder** A directory where you want the hydrated tweets to be saved (saved in a compressed format (.gz))  
- **Rate**  _SLOW, FAST, VERY_FAST, MAX_ it determines the speed (by adjusting the number of requests per second sent to the API) at which the hydrator will make requests to twitter's api and scale accordingly if it's too fast (it can only scale down for the moment)  

### Screenshots

![Alt text](screen_1.png?raw=true "Screenshot of the input phase")


![Alt text](screen_2.png?raw=true "Hydrating")
