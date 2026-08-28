package demo.override;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class Main {

    public static class Greeting extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    public static void main(String[] args) {
        System.out.println("jakarta.servlet is read from "
                + HttpServlet.class.getModule().getName()
                + ", required as "
                + Main.class.getModule().getDescriptor().requires().stream()
                        .map(java.lang.module.ModuleDescriptor.Requires::name)
                        .filter(name -> name.equals("jakarta.servlet"))
                        .findFirst()
                        .orElseThrow());
        System.out.println("servlet type: " + Greeting.class.getName());
    }
}
