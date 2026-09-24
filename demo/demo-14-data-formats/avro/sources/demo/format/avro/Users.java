package demo.format.avro;

import demo.user.User;

public class Users {

    public static User user(String name, int score) {
        return User.newBuilder().setName(name).setScore(score).build();
    }

    public static String summarize(User user) {
        return user.getName() + "=" + user.getScore();
    }
}
