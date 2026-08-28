package demo.override;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

public class TimingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            request.setAttribute("demo.override.elapsed", System.nanoTime() - started);
        }
    }
}
