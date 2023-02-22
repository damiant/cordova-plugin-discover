'use strict';

var IonicDiscover = {
    start: function () {
        return new Promise((resolve, reject) => {
            return window.cordova.exec(resolve, reject, "IonicDiscover", "start");
        });
    },

    stop: function () {
        return new Promise((resolve, reject) => {
            window.cordova.exec(resolve, reject, "IonicDiscover", "stop");
        });
    },

    getServices: function () {
        return new Promise((resolve, reject) => {
            return window.cordova.exec(resolve, reject, "IonicDiscover", "getServices");
        });
    },
};

module.exports = IonicDiscover;