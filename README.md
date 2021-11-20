# ParTweetHydrator
ParTweetHydrator is a Java tool for hydrating large datasets of tweet ids in a relatively short time.
It supports OAuth 1.0a authentication as well as OAuth 2.0 bearer tokens and is fully capable of handling twitter's rate limits on its own.
## Use
### Arguments:  
* **IDs**  Either a file or a directory containing text files made up of a single id per line, if you select a directory the hydrator will load all the text files in it and attempt to dehydrate them </br>
- **Token** **File**  An xml file like the one in the repository _(mock.xml)_   
- **Log Folder** the folder that will store the app's logs  
- **Save Folder** A directory where you want the hydrated tweets to be saved (saved in a compressed format (.gz))  
- **Rate**  _SLOW, FAST, VERY_FAST_ it determines the speed (by adjusting the number of requests per second sent to the API) at which the hydrator will make requests to twitter's api and scale accordingly if it's too fast (it can only scale down for the moment)  

### Screenshots

![Alt text](screen_1.png?raw=true "Screenshot of the input phase")


![Alt text](screen_2.png?raw=true "Hydrating")


## Requirements
Java version 16+ is required since the application uses the new record classes.

## Comparison with Twarc and Hydrator
Sample Size in IDS| Twarc | Hydrator | ParTweetHydrator  
--- | --- | --- | --- | 
1.000 | 5,98s | 6,35s | 1,177s |  
10.000 | 54,807s | 71,12s | 7,622s |  
100.000 | 548,879s | 978,26s | 77,057s |  
1.000.000 | 10.070,344s | 10.050,59s | 710,325s |  
### Time Reduction Percentage
Sample Size in IDS | Twarc | Hydrator   
--- | --- | --- |  
1.000 | -80,31% | -81,464% |  
10.000 | -86,09% | -89,29% |  
100.000 | -85,96% | -92,12% | 
1.000.000 | -92,95% | -92,93% | 
