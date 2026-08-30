package demo.override;

import jakarta.servlet.Filter;
import jakarta.servlet.jsp.JspFactory;
import java.lang.module.ModuleDescriptor;

public class Main {

    public static void main(String[] args) {
        System.out.println(Filter.class.getName() + " is read from " + Filter.class.getModule().getName());
        System.out.println(JspFactory.class.getName() + " is read from " + JspFactory.class.getModule().getName());
        for (ModuleDescriptor.Requires requires : JspFactory.class.getModule().getDescriptor().requires()) {
            if (requires.name().equals("jakarta.servlet") || requires.name().equals("jakarta.el")) {
                System.out.println("jakarta.servlet.jsp declares " + requires);
            }
        }
        System.out.println("filter: " + TimingFilter.class.getName());
    }
}
