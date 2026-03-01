package org.springframework.mytest.tx;

/**
 * @author linzherong
 * @date 2026/2/10 17:29
 */
public class BookService {

	private BookDao bookDao;

	public void setBookDao(BookDao bookDao) {
		this.bookDao = bookDao;
	}

	public void update(int id, String orderNo, String name) {
		bookDao.updateNameById(id, name);
		bookDao.updateOrderNoById(id, orderNo);
//		int i = 1 / 0;
	}

	public int insert(String orderNo, String name) {
		return bookDao.insert(orderNo, name);
	}

	public String getOrder(int id) {
		return bookDao.getOrderNo(id);
	}

}
