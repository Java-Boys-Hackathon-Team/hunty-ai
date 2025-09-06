import React from 'react'
import ReactDOM from 'react-dom/client'
import MeetingApp from './MeetingApp'

const rootEl = document.getElementById('root')!
ReactDOM.createRoot(rootEl).render(
    <React.StrictMode>
        <MeetingApp/>
    </React.StrictMode>
)

// сообщаем CSS, что приложение смонтировано,
// и можно убрать плейсхолдер #root::before
document.documentElement.classList.add('app-mounted')
