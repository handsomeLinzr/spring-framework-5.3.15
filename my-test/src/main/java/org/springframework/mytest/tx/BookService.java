package org.springframework.mytest.tx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author linzherong
 * @date 2026/2/10 17:29
 */
@Service
public class BookService {

	@Autowired
	private BookDao bookDao;

	public void setBookDao(BookDao bookDao) {
		this.bookDao = bookDao;
	}

	@Transactional
	public void update(int id, String orderNo, String name) {
		bookDao.updateNameById(id, name);
		bookDao.updateOrderNoById(id, orderNo);
		System.out.println("完成");
//		int i = 1 / 0;
	}

	public int insert(String orderNo, String name) {
		return bookDao.insert(orderNo, name);
	}

	public String getOrder(int id) {
		return bookDao.getOrderNo(id);
	}

}
