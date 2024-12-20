import burp.*;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiExtractorPlugin implements IBurpExtender, IHttpListener, ITab {
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private JTextArea postDataTextArea;
    private JTextField regexTextField;
    private JPanel mainPanel;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        callbacks.setExtensionName("API Extractor Plugin");

        // Register HTTP listener
        callbacks.registerHttpListener(this);

        // Create the configuration panel
        SwingUtilities.invokeLater(this::createUI);
    }

    private void createUI() {
        mainPanel = new JPanel(new BorderLayout());

        // JSON Data configuration
        postDataTextArea = new JTextArea(10, 30);
        mainPanel.add(new JLabel("Custom JSON Data for POST requests:"), BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(postDataTextArea), BorderLayout.CENTER);

        // Regex configuration
        regexTextField = new JTextField();
        mainPanel.add(new JLabel("Regular Expression for Response Highlighting:"), BorderLayout.SOUTH);
        mainPanel.add(regexTextField, BorderLayout.SOUTH);

        callbacks.customizeUiComponent(mainPanel);
        callbacks.addSuiteTab(this);
    }

    @Override
    public String getTabCaption() {
        return "API Extractor";
    }

    @Override
    public Component getUiComponent() {
        return mainPanel;
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        if (!messageIsRequest) {
            IResponseInfo responseInfo = helpers.analyzeResponse(messageInfo.getResponse());
            String responseBody = new String(messageInfo.getResponse()).substring(responseInfo.getBodyOffset());

            // Extract API endpoints using regex
            Pattern apiPattern = Pattern.compile("\"(https?://[^\"]+/api/[^\"]+)\"");
            Matcher apiMatcher = apiPattern.matcher(responseBody);
            while (apiMatcher.find()) {
                String apiEndpoint = apiMatcher.group(1);
                makeGetRequest(apiEndpoint);
                makePostRequest(apiEndpoint);
            }

            // Highlight matching content in the response
            String regex = regexTextField.getText();
            if (!regex.isEmpty()) {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(responseBody);
                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    callbacks.printOutput("Matched content: " + matcher.group());
                    callbacks.issueAlert("Matched content found in response: " + matcher.group());
                }
            }
        }
    }

    private void makeGetRequest(String apiEndpoint) {
        try {
            URL url = new URL(apiEndpoint);
            IHttpService httpService = helpers.buildHttpService(url.getHost(), url.getPort(), url.getProtocol());
            byte[] requestBytes = helpers.buildHttpRequest(url);
            callbacks.makeHttpRequest(httpService, requestBytes);
        } catch (Exception e) {
            callbacks.printError("Failed to make GET request to: " + apiEndpoint);
        }
    }

    private void makePostRequest(String apiEndpoint) {
        try {
            URL url = new URL(apiEndpoint);
            IHttpService httpService = helpers.buildHttpService(url.getHost(), url.getPort(), url.getProtocol());
            String postData = postDataTextArea.getText();
            byte[] requestBytes = helpers.buildHttpRequest(url, "POST", postData.getBytes());
            callbacks.makeHttpRequest(httpService, requestBytes);
        } catch (Exception e) {
            callbacks.printError("Failed to make POST request to: " + apiEndpoint);
        }
    }
}