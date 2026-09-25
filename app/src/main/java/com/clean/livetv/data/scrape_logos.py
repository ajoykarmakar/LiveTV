import urllib.request
import re
import json

url = "https://en.wikipedia.org/wiki/Lists_of_television_channels_in_India"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
html = urllib.request.urlopen(req).read().decode('utf-8')

# The page is "Lists of television channels in India". 
# But wait, it doesn't contain the channels! It contains links to OTHER lists!
print(html[:500])
