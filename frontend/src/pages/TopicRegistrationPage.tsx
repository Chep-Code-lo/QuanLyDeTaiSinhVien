import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'
import { TopicsPage } from './TopicsPage'
import { RegistrationsPage } from './RegistrationsPage'
import './AnnouncementsPage.css'

const tabs = [
  { id: 'published', label: 'Đề tài đã công bố' },
  { id: 'registrations', label: 'Hồ sơ đăng ký' },
] as const

export function TopicRegistrationPage() {
  const [params, setParams] = useSearchParams()
  const activeTab = params.get('tab') === 'registrations' ? 'registrations' : 'published'
  const selectTab = (tab: (typeof tabs)[number]['id']) => {
    setParams((previous) => {
      const next = new URLSearchParams(previous)
      if (tab === 'published') next.delete('tab')
      else next.set('tab', tab)
      return next
    })
  }

  return (
    <>
      <PageHeader title="Đề tài & đăng ký" />
      <div
        className="announcement-tabs topic-registration-tabs"
        role="tablist"
        aria-label="Đề tài và hồ sơ đăng ký"
      >
        {tabs.map((tab, index) => (
          <button
            key={tab.id}
            id={`topic-registration-tab-${tab.id}`}
            type="button"
            role="tab"
            className="announcement-tabs__tab"
            aria-selected={activeTab === tab.id}
            aria-controls={`topic-registration-panel-${tab.id}`}
            tabIndex={activeTab === tab.id ? 0 : -1}
            onClick={() => selectTab(tab.id)}
            onKeyDown={(event) => {
              let nextIndex: number
              if (event.key === 'ArrowRight' || event.key === 'ArrowLeft')
                nextIndex = (index + 1) % tabs.length
              else if (event.key === 'Home') nextIndex = 0
              else if (event.key === 'End') nextIndex = tabs.length - 1
              else return
              event.preventDefault()
              selectTab(tabs[nextIndex].id)
              document.getElementById(`topic-registration-tab-${tabs[nextIndex].id}`)?.focus()
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {tabs.map((tab) => (
        <div
          key={tab.id}
          className="topic-registration-panel"
          role="tabpanel"
          id={`topic-registration-panel-${tab.id}`}
          aria-labelledby={`topic-registration-tab-${tab.id}`}
          hidden={activeTab !== tab.id}
          tabIndex={0}
        >
          {activeTab === tab.id &&
            (tab.id === 'published' ? <TopicsPage embedded /> : <RegistrationsPage embedded />)}
        </div>
      ))}
    </>
  )
}
