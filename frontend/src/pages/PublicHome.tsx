import './PublicHome.css'

export function PublicHome({ onLogin }: { onLogin: () => void }) {
  return <div className="public-home">
    <header className="public-nav"><a className="public-brand" href="#home"><img src="/logo-hcmute.png" alt="Trường Đại học Công nghệ Kỹ thuật"/><div><b>KHOA CÔNG NGHỆ THÔNG TIN</b><small>Cổng quản lý đề tài sinh viên</small></div></a><nav><a href="#home">Trang chủ</a><a href="#guide">Hướng dẫn</a><a href="#access">Chức năng</a></nav><button className="btn btn-primary" onClick={onLogin}>Đăng nhập</button></header>
    <section className="public-hero" id="home"><div className="hero-copy"><p className="school">KHOA CÔNG NGHỆ THÔNG TIN</p><h1>Từ ý tưởng<br/>đến đề tài hoàn chỉnh.</h1><p className="hero-description">Một nơi để đề xuất ý tưởng, kết nối nhóm, đăng ký đề tài và theo dõi toàn bộ quá trình thực hiện.</p><div className="hero-buttons"><button onClick={onLogin}>Đăng nhập để khám phá đề tài →</button><a className="btn btn-secondary" href="#guide">Hướng dẫn sinh viên</a></div></div></section>
    <section className="completed" id="guide"><div className="section-heading"><h2>Quy trình dành cho sinh viên</h2><p>Theo dõi thời gian từng đợt sau khi đăng nhập.</p></div><div className="public-topic-grid">{[
      ['01','Tìm hoặc đề xuất đề tài','Xem đề tài đã công bố. Gửi ý tưởng trong thời gian nhận đề xuất và theo dõi phản hồi.'],
      ['02','Lập nhóm & đăng ký','Số thành viên theo quy định của từng đợt. Nhóm trưởng gửi lời mời và đăng ký một đề tài cho nhóm.'],
      ['03','Báo cáo & kết quả','Nhóm trưởng nộp báo cáo. Cả nhóm theo dõi phản hồi, lịch bảo vệ và điểm sau khi khoa công bố.'],
    ].map(([step,title,text])=><article className="public-topic" key={step}><span className="student-step">{step}</span><h3>{title}</h3><p>{text}</p></article>)}</div></section>
    <section className="completed" id="access"><div className="section-heading"><h2>Không gian riêng cho từng sinh viên</h2><p>Đăng nhập bằng tài khoản được cấp để xem dữ liệu thực tế của bạn và nhóm. Thông tin đăng ký, báo cáo và kết quả được kiểm soát theo quyền.</p><button className="btn btn-primary" onClick={onLogin}>Truy cập hệ thống</button></div></section>
    <footer className="public-footer"><strong>Hệ thống quản lý đề tài sinh viên</strong><a href="#home">Về đầu trang ↑</a></footer>
  </div>
}
