# Rudderstack to Split Events Integration - AWS Lambda edition

You can find a Google Cloud Function of this integration here: https://github.com/dbmartin00/rudderstack2split

Use a Rudderstack events webhook to extract, transform, and load events to Split.
This first version of the integration has not been tested for performance; Rudderstack passes events one at a time.

![alt text](http://www.cortazar-split.com/rudderstack2split.png)

1. Deploy as an AWS Lambda

I use the Eclipse AWS Toolkit to upload the lambda, but there are bound to be other options.

After you've loaded the Lambda, you must create an API for it in the API Gateway, POST method.

Edit the Integration Request to add a Mapping Template.

application/json
```
{
  "body" : $input.json('$'),
  "headers": {
    #foreach($param in $input.params().header.keySet())
    "$param": "$util.escapeJavaScript($input.params().header.get($param))" #if($foreach.hasNext),#end
    
    #end  
  }
}
```

This is necessary for the integration to work; one way to get HTTP headers through the gateway to the Lambda.

2. Register webhook as a Destination in Rudderstack

In Rudderstack, create a new Destination webhook.  Make sure it is connected to an event source.

Use these custom headers:

```
splitApiKey : Split server-side SDK key
trafficType : e.g. user or anonymous
environmentName : e.g Prod-Default
```

Provide the webhook URL you created by deploying the API Gateway POST method you created for the lambda.

3. Monitor incoming event traffic in Split Data Hub

Look for new Rudderstack events in the Split Data Hub event's stream.

For troubleshooting, check out the Cloudwatch log.

David.Martin@split.io
3-23-2021


