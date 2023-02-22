'use strict';
var exec = require('cordova/exec');

var IonicDiscover = {

    start: function() {
        return exec(null, null, "IonicDiscover", "start");
    },

    stop: function() {
        return exec(null, null, "IonicDiscover", "stop");
    },

    getServices: function() {
        return new Promise((resolve, reject) => {
            window.cordova.exec(resolve, reject, "IonicDiscover", "getServices");
        });        
    },
};

module.exports = IonicDiscover;