import java.io.*;
import java.net.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        UserManager userManager = new UserManager();
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Listening on port 8080...");

            while (true) {
                try (Socket socket = serverSocket.accept();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                    String line;
                    StringBuilder request = new StringBuilder();
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        request.append(line).append("\n");
                    }

                    if (request.length() == 0) continue;

                    String[] requestLines = request.toString().split("\n");
                    String method = requestLines[0].split(" ")[0];
                    String path = requestLines[0].split(" ")[1];

                    if (method.equals("GET")) {
                        if (path.equals("/")) {
                            writer.println("HTTP/1.1 200 OK");
                            writer.println("Content-Type: text/html; charset=UTF-8");
                            writer.println();
                            writer.println("<html><body><h1>Welcome to the server!</h1><a href='login.html'>로그인</a> | <a href='signup.html'>회원가입</a></body></html>");
                        } else if (path.equals("/login.html")) {
                            sendFile(writer, "login.html");
                        } else if (path.equals("/signup.html")) {
                            sendFile(writer, "signup.html");
                        } else if (path.equals("/home.html")) {
                            sendFile(writer, "home.html");
                        } else if (path.equals("/main.html")) {
                            sendFile(writer, "main.html");
                        } else if (path.equals("/popup.html")) {
                            sendFile(writer, "popup.html");
                        } else if (path.equals("/user.html")) {  // user.html 요청 처리 추가
                            sendFile(writer, "user.html");
                        }
                    } else if (method.equals("POST")) {
                        StringBuilder bodyBuilder = new StringBuilder();
                        while (reader.ready()) {
                            bodyBuilder.append((char) reader.read());
                        }
                        String body = bodyBuilder.toString();
                        Map<String, String> params = parseFormData(body);

                        if (path.equals("/signup")) {
                            String username = params.get("username");
                            String password = params.get("password");
                            String name = params.get("name");
                            String email = params.get("email");

                            if (!userManager.userExists(username)) {
                                userManager.saveUser(new User(username, password, name, email));
                                writer.println("HTTP/1.1 302 Found");
                                writer.println("Location: login.html");
                                writer.println();
                                writer.flush();
                            } else {
                                writer.println("HTTP/1.1 400 Bad Request");
                                writer.println("Content-Type: text/html; charset=UTF-8");
                                writer.println();
                                writer.println("<h1>이미 존재하는 아이디입니다.</h1>");
                                writer.flush();
                            }
                        } else if (path.equals("/login")) {
                            String username = params.get("username");
                            String password = params.get("password");

                            if (userManager.checkUser(username, password)) {
                                writer.println("HTTP/1.1 302 Found");
                                writer.println("Location: /home.html");
                                writer.println("Content-Length: 0");
                                writer.println();
                                writer.flush();
                            } else {
                                writer.println("HTTP/1.1 401 Unauthorized");
                                writer.println("Content-Type: text/html; charset=UTF-8");
                                writer.println();
                                writer.println("<h1>로그인 실패: 아이디 또는 비밀번호가 잘못되었습니다.</h1>");
                                writer.flush();
                            }
                        } else if (path.equals("/join")) {
                            // 참가 요청 처리
                            writer.println("HTTP/1.1 302 Found");
                            writer.println("Location: /main.html"); // main.html로 리다이렉트
                            writer.println();
                            writer.flush();
                        } 
                        else if (path.equals("/prepare")) {
                            writer.println("HTTP/1.1 200 OK"); // 응답 상태 코드
                            writer.println("Location: /popup.html");
                            writer.println("Content-Type: application/json; charset=UTF-8"); // 응답 헤더

                            writer.println("Access-Control-Allow-Origin: *"); // CORS 설정
                            writer.println(); // 빈 줄로 헤더 종료
                            writer.println("{\"status\":\"success\"}"); // JSON 응답
                            writer.flush();
                        }
                        
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("서버를 시작할 수 없습니다.");
        }
    }

    private static void sendFile(PrintWriter writer, String fileName) {
        try (BufferedReader fileReader = new BufferedReader(new FileReader(fileName))) {
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: text/html; charset=UTF-8");
            writer.println();
            String line;
            while ((line = fileReader.readLine()) != null) {
                writer.println(line);
            }
            writer.flush();
        } catch (IOException e) {
            writer.println("HTTP/1.1 404 Not Found");
            writer.println("Content-Type: text/html; charset=UTF-8");
            writer.println();
            writer.println("<h1>파일을 찾을 수 없습니다.</h1>");
            writer.flush();
        }
    }
    

    private static Map<String, String> parseFormData(String formData) throws UnsupportedEncodingException {
        Map<String, String> params = new HashMap<>();
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            String key = URLDecoder.decode(keyValue[0], "UTF-8");
            String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
            params.put(key, value);
        }
        return params;
    }

    static class User {
        private String username;
        private String password;
        private String name;
        private String email;

        public User(String username, String password, String name, String email) {
            this.username = username;
            this.password = password;
            this.name = name;
            this.email = email;
        }

        public String toJSON() {
            return String.format("{\"username\":\"%s\",\"password\":\"%s\",\"name\":\"%s\",\"email\":\"%s\"}",
                    username, password, name, email);
        }

        public static User fromJSON(String json) {
            if (json == null || json.trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid JSON: Input is null or empty.");
            }

            String[] parts = json.replace("{", "").replace("}", "").replace("\"", "").split(",");
            Map<String, String> map = new HashMap<>();

            for (String part : parts) {
                String[] keyValue = part.split(":");
                if (keyValue.length < 2) {
                    throw new IllegalArgumentException("Invalid JSON format: " + json);
                }
                map.put(keyValue[0].trim(), keyValue[1].trim());
            }

            return new User(map.get("username"), map.get("password"), map.get("name"), map.get("email"));
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    static class UserManager {
        private final String FILE_NAME = "/Users/songjun-yong/Desktop/자바프로그래밍응용/data/users.txt";

        public boolean userExists(String username) {
            List<User> users = loadUsers();
            return users.stream().anyMatch(user -> user.getUsername().equals(username));
        }

        public void saveUser(User user) {
            List<User> users = loadUsers();
            users.add(user);
            saveUsers(users);
        }

        public boolean checkUser(String username, String password) {
            List<User> users = loadUsers();
            return users.stream().anyMatch(user -> user.getUsername().equals(username) && user.getPassword().equals(password));
        }

        private List<User> loadUsers() {
            List<User> users = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(FILE_NAME))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        users.add(User.fromJSON(line));
                    } catch (IllegalArgumentException e) {
                        System.err.println("Error parsing user JSON: " + line);
                    }
                }
            } catch (IOException e) {
                // 파일이 없으면 비어 있는 리스트 반환
            }
            return users;
        }

        private void saveUsers(List<User> users) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME))) {
                for (User user : users) {
                    writer.write(user.toJSON());
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
