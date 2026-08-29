package demo.format.xml;

import demo.order.Item;
import demo.order.Order;

public class Orders {

    public static String summarize(Order order) {
        StringBuilder summary = new StringBuilder(order.getId());
        for (Item item : order.getItem()) {
            summary.append(' ').append(item.getName()).append('*').append(item.getQuantity());
        }
        return summary.toString();
    }
}
