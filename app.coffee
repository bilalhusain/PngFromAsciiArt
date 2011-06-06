express = require 'express'

app = express.createServer()
app.configure () ->
	app.use express.static (__dirname + '/static')

app.listen 8080

