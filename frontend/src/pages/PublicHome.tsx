import { useEffect, useRef, useState } from 'react'
import {
  BookOpen,
  ClipboardCheck,
  GraduationCap,
  ListChecks,
  ShieldCheck,
  UsersRound,
  X,
} from 'lucide-react'
import './PublicHome.css'

const guideSteps = [
  [
    '01',
    'Tìm hoặc đề xuất đề tài',
    'Xem đề tài đã công bố. Gửi ý tưởng trong thời gian nhận đề xuất và theo dõi phản hồi.',
  ],
  [
    '02',
    'Lập nhóm & đăng ký',
    'Số thành viên theo quy định của từng đợt. Nhóm trưởng gửi lời mời và đăng ký một đề tài cho nhóm.',
  ],
  [
    '03',
    'Báo cáo & kết quả',
    'Nhóm trưởng nộp báo cáo. Cả nhóm theo dõi phản hồi, lịch bảo vệ và điểm sau khi khoa công bố.',
  ],
]

const featureGroups = [
  {
    title: 'Sinh viên',
    icon: BookOpen,
    items: [
      'Tìm kiếm và đề xuất đề tài',
      'Tạo nhóm, mời thành viên và đăng ký đề tài',
      'Theo dõi tiến độ, nộp báo cáo và xem phản hồi',
      'Xem lịch bảo vệ và kết quả đã công bố',
    ],
  },
  {
    title: 'Giảng viên',
    icon: GraduationCap,
    items: [
      'Quản lý đề tài và theo dõi nhóm sinh viên',
      'Tạo mốc báo cáo, xem bài nộp và gửi phản hồi',
      'Chấm điểm theo bộ tiêu chí được phân công',
    ],
  },
  {
    title: 'Quản lý & quản trị',
    icon: ShieldCheck,
    items: [
      'Quản lý học thuật, đợt đăng ký và duyệt đăng ký',
      'Thiết lập thành phần đánh giá và bộ tiêu chí',
      'Phân công giảng viên, hội đồng và lịch bảo vệ',
      'Tổng hợp, công bố kết quả; quản lý tài khoản và chức vụ theo quyền',
    ],
  },
]

const popupCloseDuration = 360

export function PublicHome({ onLogin }: { onLogin: () => void }) {
  const [popup, setPopup] = useState<'guide' | 'features' | null>(null)
  const [closing, setClosing] = useState(false)
  const dialogRef = useRef<HTMLDialogElement>(null)

  const closePopup = () => setClosing(true)

  useEffect(() => {
    if (!closing) return
    const timer = window.setTimeout(() => {
      setPopup(null)
      setClosing(false)
    }, popupCloseDuration)
    return () => window.clearTimeout(timer)
  }, [closing])

  useEffect(() => {
    if (!popup) return
    const dialog = dialogRef.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog?.showModal()
    return () => {
      dialog?.close()
      document.body.style.overflow = previousOverflow
    }
  }, [popup])

  return (
    <div className="public-home">
      <header className="public-nav">
        <a className="public-brand" href="#home">
          <img
            src="/HCM-UTE_Logo.png"
            width={72}
            height={72}
            alt="Trường Đại học Công nghệ Kỹ thuật"
          />
          <div>
            <b>KHOA CÔNG NGHỆ THÔNG TIN</b>
            <small>Cổng quản lý đề tài sinh viên</small>
          </div>
        </a>
        <nav aria-label="Điều hướng trang chủ">
          <a href="#home">Trang chủ</a>
          <button
            type="button"
            onClick={() => setPopup('guide')}
            aria-haspopup="dialog"
            aria-expanded={popup === 'guide'}
          >
            Hướng dẫn
          </button>
          <button
            type="button"
            onClick={() => setPopup('features')}
            aria-haspopup="dialog"
            aria-expanded={popup === 'features'}
          >
            Chức năng
          </button>
        </nav>
        <button className="btn btn-primary" type="button" onClick={onLogin}>
          Đăng nhập
        </button>
      </header>
      <section className="public-hero" id="home">
        <div className="hero-copy">
          <p className="school">KHOA CÔNG NGHỆ THÔNG TIN</p>
          <h1>
            Từ ý tưởng
            <br />
            đến đề tài hoàn chỉnh.
          </h1>
          <p className="hero-description">
            Một nơi để đề xuất ý tưởng, kết nối nhóm, đăng ký đề tài và theo dõi toàn bộ quá trình
            thực hiện.
          </p>
          <ul className="public-hero-highlights" aria-label="Chức năng chính">
            <li>
              <span className="public-journey-icon">
                <UsersRound size={20} aria-hidden="true" />
              </span>
              <strong>Kết nối nhóm</strong>
            </li>
            <li>
              <span className="public-journey-icon">
                <ListChecks size={20} aria-hidden="true" />
              </span>
              <strong>Theo dõi tiến độ</strong>
            </li>
            <li>
              <span className="public-journey-icon">
                <ClipboardCheck size={20} aria-hidden="true" />
              </span>
              <strong>Hoàn thiện đề tài</strong>
            </li>
          </ul>
        </div>
      </section>
      <footer className="public-footer">
        <strong>Hệ thống quản lý đề tài sinh viên</strong>
        <span>Khoa Công nghệ thông tin · HCMUTE</span>
      </footer>

      {popup && (
        <dialog
          ref={dialogRef}
          className={`public-info-dialog${closing ? ' public-info-dialog--closing' : ''}`}
          aria-labelledby="public-info-title"
          onClose={() => {
            setPopup(null)
            setClosing(false)
          }}
          onCancel={(event) => {
            event.preventDefault()
            closePopup()
          }}
          onClick={(event) => {
            if (event.target === event.currentTarget) closePopup()
          }}
        >
          <div className="public-info-content">
            <header className="public-info-heading">
              <div>
                <span className="public-info-eyebrow">CỔNG QUẢN LÝ ĐỀ TÀI SINH VIÊN</span>
                <h2 id="public-info-title">
                  {popup === 'guide' ? 'Quy trình dành cho sinh viên' : 'Chức năng của hệ thống'}
                </h2>
              </div>
              <button
                className="public-info-close"
                type="button"
                onClick={closePopup}
                aria-label="Đóng hộp thoại"
              >
                <X size={20} aria-hidden="true" />
              </button>
            </header>
            {popup === 'guide' ? (
              <ol className="public-guide-steps">
                {guideSteps.map(([step, title, text]) => (
                  <li key={step}>
                    <span className="public-guide-number" aria-hidden="true">
                      {step}
                    </span>
                    <div>
                      <h3>{title}</h3>
                      <p>{text}</p>
                    </div>
                  </li>
                ))}
              </ol>
            ) : (
              <div className="public-feature-grid">
                {featureGroups.map(({ title, icon: Icon, items }) => (
                  <section className="public-feature-card" key={title}>
                    <span className="public-feature-icon">
                      <Icon size={23} strokeWidth={1.8} aria-hidden="true" />
                    </span>
                    <h3>{title}</h3>
                    <ul>
                      {items.map((item) => (
                        <li key={item}>{item}</li>
                      ))}
                    </ul>
                  </section>
                ))}
              </div>
            )}
            <footer className="public-info-footer">
              <button type="button" className="btn btn-primary" onClick={closePopup}>
                Đã hiểu
              </button>
            </footer>
          </div>
        </dialog>
      )}
    </div>
  )
}
