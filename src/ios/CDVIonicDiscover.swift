import Foundation


@objc(CDVIonicDiscover) public class CDVIonicDiscover : CDVPlugin {

    var service: IonicDiscover!
    var callbackID: String?

    public override func pluginInitialize() {
        service = IonicDiscover(namespace: "devapp")
    }

    @objc(start:) public func start(_ command: CDVInvokedUrlCommand?) {
        // unwatch previous connections
        stop(nil)

        service.start { }
    }

    @objc(stop:) public func stop(_ command: CDVInvokedUrlCommand?) {
        service.close()
        if let c = command {
            commandDelegate?.send(CDVPluginResult(status: CDVCommandStatus_OK), callbackId: c.callbackId)
        }
    }

    @objc(getServices:) public func getServices(_ command: CDVInvokedUrlCommand?) {
        if let c = command {
            let message = generateMessage(service.getServices())
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
            commandDelegate?.send(pluginResult, callbackId: c.callbackId)
        }
    }

    private func generateMessage(_ services: [Service]) -> [String: Any] {
        let arrayOfServices = services.map { (s) -> [String: Any] in
            return s.toDictionary()
        }
        return ["services": arrayOfServices]
    }
}
